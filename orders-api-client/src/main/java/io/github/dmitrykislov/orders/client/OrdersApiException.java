package io.github.dmitrykislov.orders.client;

import io.github.dmitrykislov.contractfirst.client.ApiException;
import io.github.dmitrykislov.contractfirst.client.ApiFieldError;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Raised by every generated Orders API client method when the server answers with a 4xx or 5xx.
 * See {@link ApiException} for the decoded {@link ProblemDetail} and field errors; this subtype exists
 * so callers can catch Orders failures separately from other APIs' failures.
 */
public class OrdersApiException extends ApiException {

    public OrdersApiException(HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> fieldErrors,
            @Nullable String rawBody) {
        super(status, problem, fieldErrors, rawBody);
    }
}
