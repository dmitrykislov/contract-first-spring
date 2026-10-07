package io.github.dmitrykislov.orders.client;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Client settings that are specific to the Orders API and therefore not covered by Spring Boot's
 * generic {@code spring.http.serviceclient.orders.*} properties (base URL, timeouts, redirects, SSL,
 * default headers).
 *
 * <pre>
 * orders.client.enabled=true
 * orders.client.auth.mode=static          # static | propagate | provider
 * orders.client.auth.header-name=X-API-Key
 * orders.client.auth.scheme=              # e.g. Bearer when the header is Authorization
 * orders.client.auth.token=...            # required in static mode
 * </pre>
 *
 * <p>Invariants are checked in the constructors rather than with Bean Validation so the client does not
 * force a validation provider onto every consumer; a violation still fails startup with a clear message.
 *
 * @param enabled whether the client beans are registered at all (default {@code true})
 * @param auth how the token for the contract's {@code ApiKeyAuth} scheme is obtained
 */
@ConfigurationProperties(prefix = OrdersClientProperties.PREFIX)
public record OrdersClientProperties(@DefaultValue("true") boolean enabled, @DefaultValue Auth auth) {

    public static final String PREFIX = "orders.client";

    /** Name of the HTTP service group; keys {@code spring.http.serviceclient.<group>.*}. */
    public static final String GROUP = "orders";

    /**
     * @param mode where the token comes from
     * @param headerName request header that carries the token
     * @param scheme optional authentication scheme written before the token, e.g. {@code Bearer}
     *               (a single space is inserted between scheme and token)
     * @param token the token itself; only used in {@link Mode#STATIC}
     */
    public record Auth(
            @DefaultValue("static") Mode mode,
            @DefaultValue("X-API-Key") String headerName,
            @DefaultValue("") String scheme,
            @Nullable String token) {

        public Auth {
            if (headerName == null || headerName.isBlank()) {
                throw new IllegalArgumentException(PREFIX + ".auth.header-name must not be blank");
            }
            scheme = scheme == null ? "" : scheme.strip();
            if (mode == Mode.STATIC && (token == null || token.isBlank())) {
                throw new IllegalArgumentException(
                        PREFIX + ".auth.token is required when " + PREFIX + ".auth.mode is 'static'");
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
    }

    public enum Mode {
        /** Send the token configured in {@code orders.client.auth.token} on every call. */
        STATIC,
        /**
         * Pass on the token of the current caller: the one bound via {@code TokenContext}, or failing
         * that the same header of the servlet request being processed on this thread.
         */
        PROPAGATE,
        /** Ask the application's own {@code OrdersTokenProvider} bean for a token on every call. */
        PROVIDER
    }
}
