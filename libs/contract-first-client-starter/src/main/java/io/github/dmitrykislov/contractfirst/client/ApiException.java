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

    private final String group;
    private final HttpStatusCode status;
    private final transient @Nullable ProblemDetail problem;
    private final transient List<ApiFieldError> fieldErrors;

    /**
     * @param group the HTTP service group (the API) the failure came from; lets callers that use several
     *              contract clients tell them apart even without a per-API subclass
     */
    public ApiException(String group, HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> fieldErrors,
            @Nullable String rawBody) {
        super(describe(group, status, problem, rawBody));
        this.group = group;
        this.status = status;
        this.problem = problem;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** Name of the HTTP service group whose call failed. */
    public String group() {
        return group;
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

    private static String describe(String group, HttpStatusCode status, @Nullable ProblemDetail problem, @Nullable String rawBody) {
        if (problem != null) {
            return "[%s] %d %s: %s".formatted(group, status.value(), problem.getTitle(),
                    problem.getDetail() == null ? "" : problem.getDetail());
        }
        return "[%s] %d with non-problem body: %s".formatted(group, status.value(), excerpt(rawBody));
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
