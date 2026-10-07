package io.github.dmitrykislov.contractfirst.client.auth;

import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Resolves the token for each request through the configured {@link TokenProvider} and sets it on the
 * header named in the contract's security scheme. Fails fast with a clear message rather than sending
 * an unauthenticated request that would come back as a 401, and wraps provider failures so callers
 * have one exception type for "could not authenticate".
 */
public final class TokenHeaderInterceptor implements ClientHttpRequestInterceptor {

    private final TokenProvider provider;
    private final AuthProperties auth;

    public TokenHeaderInterceptor(TokenProvider provider, AuthProperties auth) {
        this.provider = provider;
        this.auth = auth;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Optional<String> token;
        try {
            token = provider.token();
        } catch (RuntimeException providerFailure) {
            throw new ClientAuthenticationException("Token provider failed for %s %s (mode %s)"
                    .formatted(request.getMethod(), request.getURI(), auth.mode()), providerFailure);
        }
        String value = token.orElseThrow(() -> new ClientAuthenticationException(
                "No token available for %s %s (mode %s): %s".formatted(request.getMethod(), request.getURI(),
                        auth.mode(), hint(auth.mode()))));
        request.getHeaders().set(auth.headerName(), auth.headerValue(value));
        return execution.execute(request, body);
    }

    private static String hint(AuthProperties.Mode mode) {
        return switch (mode) {
            case STATIC -> "set auth.token";
            case PROPAGATE -> "wrap the call in TokenContext.with(token, ...) or call from a request that carries the header";
            case PROVIDER -> "the TokenProvider bean returned an empty token";
        };
    }
}
