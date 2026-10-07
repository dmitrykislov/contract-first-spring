package io.github.dmitrykislov.orders.client.auth;

import io.github.dmitrykislov.orders.client.OrdersClientProperties;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Resolves the token for each request through the configured {@link OrdersTokenProvider} and sets it
 * on the header named in the contract's security scheme. Fails fast with a clear message rather than
 * sending an unauthenticated request that would come back as a 401.
 */
public final class TokenHeaderInterceptor implements ClientHttpRequestInterceptor {

    private final OrdersTokenProvider provider;
    private final OrdersClientProperties.Auth auth;

    public TokenHeaderInterceptor(OrdersTokenProvider provider, OrdersClientProperties.Auth auth) {
        this.provider = provider;
        this.auth = auth;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String token = provider.token().orElseThrow(() -> new OrdersClientAuthenticationException(
                "No token available for %s %s (mode %s): %s".formatted(request.getMethod(), request.getURI(),
                        auth.mode(), hint(auth.mode()))));
        request.getHeaders().set(auth.headerName(), auth.headerValue(token));
        return execution.execute(request, body);
    }

    private static String hint(OrdersClientProperties.Mode mode) {
        return switch (mode) {
            case STATIC -> "set orders.client.auth.token";
            case PROPAGATE -> "wrap the call in TokenContext.with(token, ...) or call from a request that carries the header";
            case PROVIDER -> "the OrdersTokenProvider bean returned an empty token";
        };
    }
}
