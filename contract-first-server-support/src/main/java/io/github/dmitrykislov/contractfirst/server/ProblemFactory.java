package io.github.dmitrykislov.contractfirst.server;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Shapes Spring's {@link ProblemDetail} (RFC 9457) consistently: a stable {@code type} URI under a
 * configurable namespace, an absolute {@code instance}, and field-level {@code errors} as an extension.
 */
public class ProblemFactory {

    public static final String ERRORS_PROPERTY = "errors";

    private final String typeNamespace;

    public ProblemFactory(String typeNamespace) {
        this.typeNamespace = typeNamespace;
    }

    public ProblemDetail of(HttpStatusCode status, String title, @Nullable String detail, HttpServletRequest request) {
        return of(status, title, detail, request, List.of());
    }

    public ProblemDetail of(HttpStatusCode status, String title, @Nullable String detail, HttpServletRequest request,
            List<ProblemFieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(typeFor(title));
        problem.setInstance(instanceOf(request));
        if (!errors.isEmpty()) {
            problem.setProperty(ERRORS_PROPERTY, List.copyOf(errors));
        }
        return problem;
    }

    /** Completes a {@link ProblemDetail} produced by Spring itself so it carries the same namespace and absolute instance. */
    public ProblemDetail decorate(ProblemDetail problem, HttpServletRequest request) {
        if (problem.getTitle() == null) {
            problem.setTitle(reasonPhrase(HttpStatusCode.valueOf(problem.getStatus())));
        }
        if (problem.getType() == null || "about:blank".equals(problem.getType().toString())) {
            problem.setType(typeFor(problem.getTitle()));
        }
        if (problem.getInstance() == null || !problem.getInstance().isAbsolute()) {
            problem.setInstance(instanceOf(request));
        }
        return problem;
    }

    public ProblemFieldError fieldError(String field, String message, @Nullable Object rejectedValue) {
        String echoed = rejectedValue != null && isSafeToEcho(rejectedValue) ? String.valueOf(rejectedValue) : null;
        return new ProblemFieldError(field, message, echoed);
    }

    /** Reason phrase for standard codes; a neutral title for non-standard ones (e.g. 599) instead of an exception. */
    public static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "HTTP " + status.value();
    }

    public URI typeFor(String title) {
        // String concatenation on purpose: URI.resolve() does not work for opaque namespaces such as urn:.
        return URI.create(typeNamespace + slug(title));
    }

    private static URI instanceOf(HttpServletRequest request) {
        // The contracts type 'instance' as an absolute URI, so rebuild the full request URL.
        return ServletUriComponentsBuilder.fromRequestUri(request).build().toUri();
    }

    private static boolean isSafeToEcho(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof Enum<?>
                || (value instanceof CharSequence cs && cs.length() <= 64);
    }

    private static String slug(String title) {
        return title.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
