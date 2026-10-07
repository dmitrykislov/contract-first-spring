package io.github.dmitrykislov.contractfirst.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

class ProblemResponseErrorHandlerTest {

    /** What an API's own exception type looks like. */
    static final class ThingsApiException extends ApiException {
        public ThingsApiException(String group, HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> errors, @Nullable String raw) {
            super(group, status, problem, errors, raw);
        }
    }

    private final JsonMapper mapper = JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    private final ProblemResponseErrorHandler handler = new ProblemResponseErrorHandler("things", mapper, ThingsApiException::new);
    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://api/things"));

    @Test
    void decodesProblemDetailAndTypedFieldErrors() {
        String body = """
                {"type":"https://api/problems/validation-failed","title":"Validation failed","status":400,
                 "detail":"Request violates the contract","instance":"http://api/things",
                 "errors":[{"field":"lines[0].quantity","message":"must be >= 1","rejectedValue":"0"},{"field":"notes","message":"too long"}]}
                """;
        assertThatThrownBy(() -> handler.handle(request, response(HttpStatus.BAD_REQUEST, MediaType.APPLICATION_PROBLEM_JSON, body)))
                .isInstanceOfSatisfying(ThingsApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.problem()).get().satisfies(p -> {
                        assertThat(p.getType()).hasToString("https://api/problems/validation-failed");
                        assertThat(p.getDetail()).isEqualTo("Request violates the contract");
                    });
                    assertThat(ex.fieldErrors()).containsExactly(
                            new ApiFieldError("lines[0].quantity", "must be >= 1", "0"),
                            new ApiFieldError("notes", "too long", null));
                })
                .hasMessage("[things] 400 Validation failed: Request violates the contract");
    }

    @Test
    void acceptsPlainJsonProblemsAndToleratesMalformedErrors() {
        String body = "{\"title\":\"Nope\",\"status\":409,\"errors\":\"not-a-list\"}";
        assertThatThrownBy(() -> handler.handle(request, response(HttpStatus.CONFLICT, MediaType.APPLICATION_JSON, body)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.problem()).get().extracting(ProblemDetail::getTitle).isEqualTo("Nope");
                    assertThat(ex.fieldErrors()).isEmpty();
                });
    }

    @Test
    void keepsOnlyAnExcerptOfNonProblemBodies() {
        String html = "<html>" + "x".repeat(2_000) + "</html>";
        assertThatThrownBy(() -> handler.handle(request, response(HttpStatus.BAD_GATEWAY, MediaType.TEXT_HTML, html)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.problem()).isEmpty();
                    assertThat(ex.getMessage()).contains("502 with non-problem body").contains("[things]").contains("... (" + html.length() + " chars)")
                            .hasSizeLessThan(ApiException.RAW_BODY_EXCERPT_LENGTH + 80);
                });
        assertThatThrownBy(() -> handler.handle(request, response(HttpStatus.NOT_FOUND, null, "")))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getMessage()).contains("<empty>"));
    }

    private static MockClientHttpResponse response(HttpStatus status, @Nullable MediaType type, String body) {
        MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
        if (type != null) {
            response.getHeaders().setContentType(type);
        }
        return response;
    }
}
