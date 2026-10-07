package io.github.dmitrykislov.contractfirst.client;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Creates the exception an API's client throws; usually a constructor reference of an {@link ApiException} subclass. */
@FunctionalInterface
public interface ApiExceptionFactory {

    ApiException create(HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> fieldErrors,
            @Nullable String rawBody);
}
