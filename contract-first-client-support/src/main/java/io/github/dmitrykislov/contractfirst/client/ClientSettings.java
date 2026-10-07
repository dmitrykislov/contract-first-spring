package io.github.dmitrykislov.contractfirst.client;

import io.github.dmitrykislov.contractfirst.client.auth.AuthProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything this library configures for one contract client, bound from
 * {@code contract-first.clients.<group>.*} on top of {@code contract-first.clients.defaults.*}.
 * Connection settings (base URL, timeouts, TLS) are Spring Boot's own {@code spring.http.serviceclient.<group>.*}.
 *
 * @param enabled register the client at all (default {@code true})
 * @param auth token mode and header
 * @param retry retries for idempotent calls
 * @param requestId correlation-id propagation
 */
public record ClientSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue AuthProperties auth,
        @DefaultValue RetryProperties retry,
        @DefaultValue RequestIdProperties requestId) {}
