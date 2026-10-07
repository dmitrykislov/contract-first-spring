package io.github.dmitrykislov.orders.server.web;

import io.github.dmitrykislov.orders.server.domain.OrdersDomainException;
import io.github.dmitrykislov.orders.server.model.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.context.i18n.LocaleContextHolder;
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
 * Renders every failure as Spring's {@link ProblemDetail} with {@code application/problem+json}.
 *
 * <p>Framework exceptions (type mismatch, missing header, unreadable body, unsupported media type,
 * unknown route, ...) are already mapped to the right status by {@link ResponseEntityExceptionHandler};
 * this class only decorates the resulting problem with the API's type namespace and absolute instance
 * URI, and enriches validation failures with field-level {@code errors}. Domain failures are matched
 * exhaustively over the sealed hierarchy, so a new case is a compile error until it has a status.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemFactory problems;

    public ApiExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(OrdersDomainException.class)
    ResponseEntity<ProblemDetail> domain(OrdersDomainException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex) {
            case OrdersDomainException.OrderNotFound _ -> HttpStatus.NOT_FOUND;
            case OrdersDomainException.UnknownSku _ -> HttpStatus.UNPROCESSABLE_CONTENT;
            case OrdersDomainException.MixedCurrencies _ -> HttpStatus.UNPROCESSABLE_CONTENT;
            case OrdersDomainException.IllegalOrderState _ -> HttpStatus.CONFLICT;
            case OrdersDomainException.IdempotencyKeyReused _ -> HttpStatus.CONFLICT;
            case OrdersDomainException.VersionMismatch _ -> HttpStatus.PRECONDITION_FAILED;
        };
        return problemResponse(problems.of(status, status.getReasonPhrase(), ex.getMessage(), request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problemResponse(problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", request));
    }

    /** {@code @Valid @RequestBody} failures reported by the argument resolver. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> problems.fieldError(fe.getField(), String.valueOf(fe.getDefaultMessage()), fe.getRejectedValue()))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors, request), headers, status, request);
    }

    /**
     * Spring's built-in method validation: constraint violations on parameters, headers and path
     * variables arrive as plain results, while a {@code @Valid} body that fails arrives as
     * {@link ParameterErrors} with per-field details.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
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

    /** Every response produced by the base class passes through here; make it ours. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpServletRequest servletRequest = servletRequest(request);
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            // Same contract as the base implementation: the exception carries its own ProblemDetail
            // (e.g. "Required header 'Idempotency-Key' is not present."), possibly localised.
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

    private ProblemDetail validationProblem(List<FieldError> errors, WebRequest request) {
        return problems.of(HttpStatus.BAD_REQUEST, "Validation failed", "Request violates the contract",
                servletRequest(request), errors);
    }

    private static ResponseEntity<ProblemDetail> problemResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
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
