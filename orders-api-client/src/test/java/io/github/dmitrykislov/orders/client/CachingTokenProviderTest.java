package io.github.dmitrykislov.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dmitrykislov.orders.client.auth.CachingTokenProvider;
import io.github.dmitrykislov.orders.client.auth.OrdersTokenProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CachingTokenProviderTest {

    private final AtomicInteger fetches = new AtomicInteger();
    private final AtomicReference<Optional<String>> next = new AtomicReference<>(Optional.of("t1"));
    private final OrdersTokenProvider delegate = () -> {
        fetches.incrementAndGet();
        return next.get();
    };
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void servesCachedTokenUntilExpiryThenFetchesAgain() {
        CachingTokenProvider provider = new CachingTokenProvider(delegate, Duration.ofMinutes(5), clock);

        assertThat(provider.token()).contains("t1");
        assertThat(provider.token()).contains("t1");
        assertThat(fetches).hasValue(1);

        now.set(now.get().plus(Duration.ofMinutes(5)));
        next.set(Optional.of("t2"));
        assertThat(provider.token()).contains("t2");
        assertThat(fetches).hasValue(2);
    }

    @Test
    void invalidateForcesAFetchAndEmptyResultsAreNotCached() {
        CachingTokenProvider provider = new CachingTokenProvider(delegate, Duration.ofHours(1), clock);
        assertThat(provider.token()).contains("t1");

        provider.invalidate();
        next.set(Optional.empty());
        assertThat(provider.token()).isEmpty();
        assertThat(provider.token()).isEmpty();
        assertThat(fetches).hasValue(3); // empty answers are retried every time

        next.set(Optional.of("t3"));
        assertThat(provider.token()).contains("t3");
    }

    @Test
    void rejectsNonPositiveTimeToLive() {
        assertThatThrownBy(() -> new CachingTokenProvider(delegate, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
