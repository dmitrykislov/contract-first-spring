package io.github.dmitrykislov.contractfirst.client;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retries for calls that are safe to repeat. Designed to be nested under an API's own properties,
 * e.g. {@code orders.client.retry.*}.
 *
 * <p>Only idempotent requests are retried: GET, HEAD, OPTIONS, PUT and DELETE always; POST and PATCH
 * only when they carry the configured idempotency header, because then the server guarantees a replay
 * is harmless. Retried failures are connection-level {@link java.io.IOException}s and the configured
 * response statuses (by default the gateway family 502, 503, 504).
 *
 * @param enabled switch
 * @param maxAttempts total attempts including the first; {@code 1} disables retries
 * @param initialBackoff delay before the second attempt
 * @param multiplier growth factor applied to the delay after each attempt
 * @param maxBackoff upper bound for a single delay
 * @param retryableStatuses response statuses treated as transient
 * @param idempotencyHeader header whose presence makes a POST or PATCH retryable
 */
public record RetryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("200ms") Duration initialBackoff,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("2s") Duration maxBackoff,
        @DefaultValue({"502", "503", "504"}) Set<Integer> retryableStatuses,
        @DefaultValue("Idempotency-Key") String idempotencyHeader) {

    public RetryProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("retry.max-attempts must be at least 1");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("retry.multiplier must be at least 1.0");
        }
        retryableStatuses = retryableStatuses == null ? Set.of() : Set.copyOf(retryableStatuses);
    }

    public boolean active() {
        return enabled && maxAttempts > 1;
    }
}
