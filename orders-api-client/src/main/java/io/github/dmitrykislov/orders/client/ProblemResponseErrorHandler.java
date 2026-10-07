package io.github.dmitrykislov.orders.client;

import io.github.dmitrykislov.orders.client.model.FieldError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts 4xx/5xx responses into {@link OrdersApiException}, decoding the body as Spring's
 * {@link ProblemDetail} when the server says it is one ({@code application/problem+json}) or when it
 * merely looks like one. The contract's {@code errors} extension is lifted into typed
 * {@link FieldError}s. Anything else is kept as raw text for diagnostics.
 */
final class ProblemResponseErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private static final String ERRORS_PROPERTY = "errors";

    private final JsonMapper jsonMapper;
    private final JavaType fieldErrorList;

    ProblemResponseErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.fieldErrorList = jsonMapper.getTypeFactory().constructCollectionType(List.class, FieldError.class);
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        ProblemDetail problem = decode(response.getHeaders().getContentType(), body);
        throw new OrdersApiException(response.getStatusCode(), problem, fieldErrors(problem), body);
    }

    private @Nullable ProblemDetail decode(@Nullable MediaType contentType, String body) {
        if (body.isBlank() || contentType == null) {
            return null;
        }
        boolean jsonLike = contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                || contentType.isCompatibleWith(MediaType.APPLICATION_JSON);
        if (!jsonLike) {
            return null;
        }
        try {
            ProblemDetail problem = jsonMapper.readValue(body, ProblemDetail.class);
            return problem.getTitle() == null && problem.getDetail() == null && problem.getStatus() == 0 ? null : problem;
        } catch (RuntimeException notAProblem) {
            return null;
        }
    }

    private List<FieldError> fieldErrors(@Nullable ProblemDetail problem) {
        if (problem == null || problem.getProperties() == null) {
            return List.of();
        }
        Object raw = problem.getProperties().get(ERRORS_PROPERTY);
        if (raw == null) {
            return List.of();
        }
        try {
            return jsonMapper.convertValue(raw, fieldErrorList);
        } catch (RuntimeException malformed) {
            return List.of();
        }
    }
}
