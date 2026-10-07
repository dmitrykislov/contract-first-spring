package io.github.dmitrykislov.orders.server.web;

import io.github.dmitrykislov.orders.server.model.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Single place that shapes Spring's {@link ProblemDetail} (RFC 9457) the way the contract's
 * {@code Problem} schema expects: a stable {@code type} URI under a documentation namespace, an
 * absolute {@code instance}, and field-level {@code errors} as an extension property.
 */
@Component
public class ProblemFactory {

    public static final URI TYPE_NAMESPACE = URI.create("https://orders.example.com/problems/");
    public static final String ERRORS_PROPERTY = "errors";

    public ProblemDetail of(HttpStatusCode status, String title, @Nullable String detail, HttpServletRequest request) {
        return of(status, title, detail, request, List.of());
    }

    public ProblemDetail of(HttpStatusCode status, String title, @Nullable String detail, HttpServletRequest request,
            List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(TYPE_NAMESPACE.resolve(slug(title)));
        // The contract types 'instance' as an absolute URI, so rebuild the full request URL.
        problem.setInstance(ServletUriComponentsBuilder.fromRequestUri(request).build().toUri());
        if (!errors.isEmpty()) {
            problem.setProperty(ERRORS_PROPERTY, List.copyOf(errors));
        }
        return problem;
    }

    /**
     * Completes a {@link ProblemDetail} produced by Spring itself (e.g. by
     * {@code ResponseEntityExceptionHandler}) so it carries the same type namespace and absolute
     * instance as problems created here.
     */
    public ProblemDetail decorate(ProblemDetail problem, HttpServletRequest request) {
        if (problem.getTitle() == null) {
            problem.setTitle(reasonPhrase(HttpStatusCode.valueOf(problem.getStatus())));
        }
        if (problem.getType() == null || "about:blank".equals(problem.getType().toString())) {
            problem.setType(TYPE_NAMESPACE.resolve(slug(problem.getTitle())));
        }
        if (problem.getInstance() == null || !problem.getInstance().isAbsolute()) {
            problem.setInstance(ServletUriComponentsBuilder.fromRequestUri(request).build().toUri());
        }
        return problem;
    }

    public FieldError fieldError(String field, String message, @Nullable Object rejectedValue) {
        FieldError error = new FieldError(field, message);
        if (rejectedValue != null && isSafeToEcho(rejectedValue)) {
            error.rejectedValue(String.valueOf(rejectedValue));
        }
        return error;
    }

    private static boolean isSafeToEcho(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof Enum<?>
                || (value instanceof CharSequence cs && cs.length() <= 64);
    }

    /** Reason phrase for standard codes; a neutral title for non-standard ones (e.g. 599) instead of an exception. */
    public static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "HTTP " + status.value();
    }

    private static String slug(String title) {
        return title.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
