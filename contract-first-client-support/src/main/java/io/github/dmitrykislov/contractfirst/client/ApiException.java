package io.github.dmitrykislov.contractfirst.client;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Raised by a contract client method when the server answers with a 4xx or 5xx. Carries the decoded
 * RFC 9457 {@link ProblemDetail} when the server sent one, so callers can branch on {@code getType()}
 * or {@link #status()} instead of parsing messages, plus field-level errors for validation failures.
 * APIs typically subclass it ({@code OrdersApiException}) so callers can catch one API's failures.
 */
public class ApiException extends RuntimeException {

    /** Bodies that are not problems (proxy error pages, HTML) are kept only as a short excerpt. */
    public static final int RAW_BODY_EXCERPT_LENGTH = 256;

    private final HttpStatusCode status;
    private final transient @Nullable ProblemDetail problem;
    private final transient List<ApiFieldError> fieldErrors;

    public ApiException(HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> fieldErrors,
            @Nullable String rawBody) {
        super(describe(status, problem, rawBody));
        this.status = status;
        this.problem = problem;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public HttpStatusCode status() {
        return status;
    }

    public Optional<ProblemDetail> problem() {
        return Optional.ofNullable(problem);
    }

    /** Field-level errors from the problem's {@code errors} extension; empty unless validation failed. */
    public List<ApiFieldError> fieldErrors() {
        return fieldErrors;
    }

    private static String describe(HttpStatusCode status, @Nullable ProblemDetail problem, @Nullable String rawBody) {
        if (problem != null) {
            return "%d %s: %s".formatted(status.value(), problem.getTitle(),
                    problem.getDetail() == null ? "" : problem.getDetail());
        }
        return "%d with non-problem body: %s".formatted(status.value(), excerpt(rawBody));
    }

    private static String excerpt(@Nullable String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "<empty>";
        }
        String oneLine = rawBody.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= RAW_BODY_EXCERPT_LENGTH
                ? oneLine
                : oneLine.substring(0, RAW_BODY_EXCERPT_LENGTH) + "... (" + rawBody.length() + " chars)";
    }
}
