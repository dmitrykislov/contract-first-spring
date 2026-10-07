package io.github.dmitrykislov.contractfirst.client.errors;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Creates the exception an API's client throws; usually a constructor reference of an {@link ApiException} subclass. */
@FunctionalInterface
public interface ApiExceptionFactory {

    ApiException create(String group, HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> fieldErrors,
            @Nullable String rawBody);

    /** Builds a factory for an {@link ApiException} subclass that exposes the canonical five-argument constructor. */
    static ApiExceptionFactory forType(Class<? extends ApiException> type) {
        try {
            var constructor = type.getConstructor(String.class, HttpStatusCode.class, ProblemDetail.class, List.class, String.class);
            return (group, status, problem, errors, raw) -> {
                try {
                    return constructor.newInstance(group, status, problem, errors, raw);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot instantiate " + type.getName(), e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type.getName() + " must declare a public constructor "
                    + "(String group, HttpStatusCode status, ProblemDetail problem, List<ApiFieldError> fieldErrors, String rawBody)", e);
        }
    }
}
