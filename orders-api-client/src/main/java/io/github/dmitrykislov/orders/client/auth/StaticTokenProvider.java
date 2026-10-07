package io.github.dmitrykislov.orders.client.auth;

import java.util.Optional;

/** Always returns the configured token. */
public final class StaticTokenProvider implements OrdersTokenProvider {

    private final Optional<String> token;

    public StaticTokenProvider(String token) {
        this.token = Optional.of(token);
    }

    @Override
    public Optional<String> token() {
        return token;
    }
}
