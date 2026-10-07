package io.github.dmitrykislov.contractfirst.server;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every failure as a {@link ProblemDetail} with {@code application/problem+json}.
 *
 * <p>Framework exceptions keep the status mapping of {@link ResponseEntityExceptionHandler} and are
 * decorated with the configured type namespace and an absolute instance. Validation failures carry the
 * {@code errors} extension. Domain exceptions are mapped through {@link DomainExceptionMapper} beans;
 * anything unmapped is a 500 with a neutral message and a logged stack trace.
 *
 * <p>Ordered last on purpose: across several {@code @ControllerAdvice} beans Spring uses the first
 * advice (by order) that has any matching handler, so a catch-all here must not shadow an
 * application's own, more specific advice.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ContractExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ContractExceptionHandler.class);

    private final ProblemFactory problems;
    private final List<DomainExceptionMapper> mappers;

    public ContractExceptionHandler(ProblemFactory problems, List<DomainExceptionMapper> mappers) {
        this.problems = problems;
        this.mappers = List.copyOf(mappers);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unmappedOrDomain(Exception ex, HttpServletRequest request) {
        Optional<HttpStatusCode> mapped = mappers.stream()
                .map(mapper -> mapper.statusOf(ex))
                .flatMap(Optional::stream)
                .findFirst();
        if (mapped.isPresent()) {
            HttpStatusCode status = mapped.get();
            return problemResponse(problems.of(status, ProblemFactory.reasonPhrase(status), ex.getMessage(), request));
        }
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problemResponse(problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", request));
    }

    /** {@code @Valid @RequestBody} failures reported by the argument resolver. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> problems.fieldError(fe.getField(), String.valueOf(fe.getDefaultMessage()), fe.getRejectedValue()))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors, request), headers, status, request);
    }

    /**
     * Spring's built-in method validation: parameter, header and path-variable violations arrive as plain
     * results, a {@code @Valid} body that fails arrives as {@link ParameterErrors} with per-field details.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemFieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> switch (result) {
                    case ParameterErrors body -> body.getFieldErrors().stream()
                            .map(fe -> problems.fieldError(fe.getField(), String.valueOf(fe.getDefaultMessage()), fe.getRejectedValue()));
                    default -> result.getResolvableErrors().stream()
                            .map(error -> problems.fieldError(wireName(result.getMethodParameter()),
                                    String.valueOf(error.getDefaultMessage()), result.getArgument()));
                })
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors, request), headers, status, request);
    }

    /** Every response produced by the base class passes through here. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        ProblemDetail problem = body instanceof ProblemDetail detail
                ? problems.decorate(detail, servletRequest)
                : problems.of(statusCode, ProblemFactory.reasonPhrase(statusCode), null, servletRequest);
        HttpHeaders problemHeaders = new HttpHeaders();
        problemHeaders.putAll(headers);
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(statusCode).headers(problemHeaders).body(problem);
    }

    private ProblemDetail validationProblem(List<ProblemFieldError> errors, WebRequest request) {
        return problems.of(HttpStatus.BAD_REQUEST, "Validation failed", "Request violates the contract",
                ((ServletWebRequest) request).getRequest(), errors);
    }

    private static ResponseEntity<ProblemDetail> problemResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** Reports the name a client sees on the wire (header/query/path name), not the Java parameter name. */
    private static String wireName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) {
            return header.value();
        }
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null && !query.value().isEmpty()) {
            return query.value();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.value().isEmpty()) {
            return path.value();
        }
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }
}
