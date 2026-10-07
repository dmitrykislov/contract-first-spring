package io.github.dmitrykislov.contractfirst.client;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Sets the correlation header from the MDC when the caller did not set one explicitly. An explicit
 * header (a generated method's {@code xRequestId} parameter) always wins.
 */
public final class RequestIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final RequestIdProperties properties;

    public RequestIdPropagationInterceptor(RequestIdProperties properties) {
        this.properties = properties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsHeader(properties.headerName())) {
            String current = MDC.get(properties.mdcKey());
            if (current != null && !current.isBlank()) {
                request.getHeaders().set(properties.headerName(), current);
            }
        }
        return execution.execute(request, body);
    }
}
