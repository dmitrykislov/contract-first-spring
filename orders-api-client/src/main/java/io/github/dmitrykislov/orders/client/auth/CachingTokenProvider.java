package io.github.dmitrykislov.orders.client.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Decorator for {@code provider} mode: asks the delegate at most once per time-to-live and serves the
 * cached token in between, so fetching from an identity provider or secret manager does not happen on
 * every request. Call {@link #invalidate()} after a 401 to force a fresh fetch on the next call.
 *
 * <p>Thread-safe; concurrent callers after expiry may each trigger one fetch, which is acceptable for
 * idempotent token endpoints and keeps the implementation lock-free.
 */
public final class CachingTokenProvider implements OrdersTokenProvider {

    private record Cached(String token, Instant expiresAt) {}

    private final OrdersTokenProvider delegate;
    private final Duration timeToLive;
    private final Clock clock;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public CachingTokenProvider(OrdersTokenProvider delegate, Duration timeToLive) {
        this(delegate, timeToLive, Clock.systemUTC());
    }

    public CachingTokenProvider(OrdersTokenProvider delegate, Duration timeToLive, Clock clock) {
        if (timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("timeToLive must be positive");
        }
        this.delegate = delegate;
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    @Override
    public Optional<String> token() {
        Cached current = cache.get();
        Instant now = clock.instant();
        if (current != null && now.isBefore(current.expiresAt())) {
            return Optional.of(current.token());
        }
        Optional<String> fetched = delegate.token();
        fetched.ifPresent(token -> cache.set(new Cached(token, now.plus(timeToLive))));
        return fetched;
    }

    /** Drops the cached token; the next call fetches again. */
    public void invalidate() {
        cache.set(null);
    }
}
