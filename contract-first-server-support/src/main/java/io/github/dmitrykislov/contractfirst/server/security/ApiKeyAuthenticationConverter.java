package io.github.dmitrykislov.contractfirst.server.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** Reads the API-key header; an absent header means "not attempted", which the entry point reports as missing. */
public final class ApiKeyAuthenticationConverter implements AuthenticationConverter {

    private final String headerName;

    public ApiKeyAuthenticationConverter(String headerName) {
        this.headerName = headerName;
    }

    public String headerName() {
        return headerName;
    }

    @Override
    public @Nullable Authentication convert(HttpServletRequest request) {
        String presented = request.getHeader(headerName);
        return presented == null ? null : ApiKeyAuthentication.presented(presented);
    }
}
