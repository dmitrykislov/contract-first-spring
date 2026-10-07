package io.github.dmitrykislov.contractfirst.client.config;

import io.github.dmitrykislov.contractfirst.client.correlation.RequestIdProperties;
import io.github.dmitrykislov.contractfirst.client.retry.RetryProperties;
import io.github.dmitrykislov.contractfirst.client.auth.AuthProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything the client starter configures for one contract client (one HTTP service group), bound from
 * {@code openapi.clients.groups.<group>.*} on top of {@code openapi.clients.defaults.*}. Connection settings
 * (base URL, timeouts, TLS) are Spring Boot's own {@code spring.http.serviceclient.<group>.*}.
 *
 * @param enabled register the client at all (default {@code true})
 * @param auth token mode and header
 * @param retry retries for idempotent calls
 * @param requestId correlation-id propagation
 */
public record ContractClientProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue AuthProperties auth,
        @DefaultValue RetryProperties retry,
        @DefaultValue RequestIdProperties requestId) {}
