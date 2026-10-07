package io.github.dmitrykislov.contractfirst.client.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a contract client obtains and sends its token. Designed to be nested under an API's own
 * {@code @ConfigurationProperties} record, e.g. {@code orders.client.auth.*}.
 *
 * <p>Invariants are checked in the constructor rather than with Bean Validation so the library does not
 * force a validation provider onto every consumer; a violation still fails startup with a clear message.
 *
 * @param mode where the token comes from
 * @param headerName request header that carries the token
 * @param scheme optional authentication scheme written before the token, e.g. {@code Bearer}
 *               (a single space is inserted between scheme and token)
 * @param token the token itself; only used in {@link Mode#STATIC}
 */
public record AuthProperties(
        @DefaultValue("static") Mode mode,
        @DefaultValue("X-API-Key") String headerName,
        @DefaultValue("") String scheme,
        @Nullable String token) {

    public AuthProperties {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("auth.header-name must not be blank");
        }
        scheme = scheme == null ? "" : scheme.strip();
        if (mode == null) {
            mode = Mode.STATIC;
        }
        if (mode == Mode.STATIC && (token == null || token.isBlank())) {
            throw new IllegalArgumentException("auth.token is required when auth.mode is 'static'");
        }
    }

    /** The header value for a raw token: {@code <scheme> <token>} or just the token. */
    public String headerValue(String rawToken) {
        return scheme.isEmpty() ? rawToken : scheme + " " + rawToken;
    }

    /** Inverse of {@link #headerValue}: strips the scheme from a header value that carries one. */
    public String rawToken(String headerValue) {
        String value = headerValue.strip();
        if (!scheme.isEmpty() && value.regionMatches(true, 0, scheme + " ", 0, scheme.length() + 1)) {
            return value.substring(scheme.length() + 1).strip();
        }
        return value;
    }

    public enum Mode {
        /** Send the configured {@code token} on every call. */
        STATIC,
        /**
         * Pass on the token of the current caller: the one bound via {@link TokenContext}, or failing
         * that the same header of the servlet request being processed on this thread.
         */
        PROPAGATE,
        /** Ask the application's own {@link TokenProvider} bean for a token on every call. */
        PROVIDER
    }
}
