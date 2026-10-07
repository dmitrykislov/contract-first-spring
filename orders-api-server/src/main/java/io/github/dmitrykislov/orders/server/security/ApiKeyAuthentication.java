package io.github.dmitrykislov.orders.server.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A presented or verified API key. The key itself is never kept after verification. */
public final class ApiKeyAuthentication extends AbstractAuthenticationToken {

    public static final String ROLE = "ROLE_API_CLIENT";

    private final String principal;

    private ApiKeyAuthentication(String principal, boolean authenticated) {
        super(authenticated ? List.of(new SimpleGrantedAuthority(ROLE)) : List.of());
        this.principal = principal;
        setAuthenticated(authenticated);
    }

    /** The unverified key as presented in the request. */
    public static ApiKeyAuthentication presented(String apiKey) {
        return new ApiKeyAuthentication(apiKey, false);
    }

    /** A verified client; the key is replaced by an opaque principal name. */
    public static ApiKeyAuthentication verified() {
        return new ApiKeyAuthentication("api-client", true);
    }

    @Override
    public Object getCredentials() {
        return isAuthenticated() ? "" : principal;
    }

    @Override
    public Object getPrincipal() {
        return isAuthenticated() ? principal : "unverified";
    }
}
