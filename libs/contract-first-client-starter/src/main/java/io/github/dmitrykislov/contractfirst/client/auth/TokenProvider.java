package io.github.dmitrykislov.contractfirst.client.auth;

import java.util.Optional;

/**
 * Supplies the token sent on each call of a contract client. Built-in implementations cover the
 * {@code static} and {@code propagate} modes; in {@code provider} mode the application registers its
 * own, typically fetching and caching a token from an identity provider or secret store.
 *
 * <p>Called once per request on the calling thread; implementations must be thread-safe.
 */
@FunctionalInterface
public interface TokenProvider {

    /** The raw token without any scheme prefix, or empty when none is available. */
    Optional<String> token();
}
