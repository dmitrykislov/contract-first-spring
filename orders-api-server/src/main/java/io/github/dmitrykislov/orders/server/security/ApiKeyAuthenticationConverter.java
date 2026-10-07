package io.github.dmitrykislov.orders.server.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** Reads the contract's {@code X-API-Key} header; an absent header means "not attempted", handled by the entry point. */
public final class ApiKeyAuthenticationConverter implements AuthenticationConverter {

    public static final String HEADER = "X-API-Key";

    @Override
    public @Nullable Authentication convert(HttpServletRequest request) {
        String presented = request.getHeader(HEADER);
        return presented == null ? null : ApiKeyAuthentication.presented(presented);
    }
}
