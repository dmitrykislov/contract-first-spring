package io.github.dmitrykislov.orders.client.auth;

import java.util.Optional;

/**
 * Supplies the token sent on each Orders API call. The client ships providers for the
 * {@code static} and {@code propagate} modes; in {@code provider} mode the application registers its
 * own bean, typically fetching and caching a token from an identity provider or secret store.
 *
 * <p>Called once per request on the calling thread; implementations must be thread-safe.
 */
@FunctionalInterface
public interface OrdersTokenProvider {

    /** The raw token without any scheme prefix, or empty when none is available. */
    Optional<String> token();
}
