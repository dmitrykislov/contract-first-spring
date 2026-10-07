package io.github.dmitrykislov.contractfirst.client.auth;

/**
 * Raised before a request is sent when no token can be obtained for it, either because the provider
 * returned nothing or because it failed (identity provider down, secret store unreachable).
 */
public class ClientAuthenticationException extends RuntimeException {

    public ClientAuthenticationException(String message) {
        super(message);
    }

    public ClientAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
