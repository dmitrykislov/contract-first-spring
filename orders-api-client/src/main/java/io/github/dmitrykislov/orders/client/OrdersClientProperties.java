package io.github.dmitrykislov.orders.client;

import io.github.dmitrykislov.contractfirst.client.RequestIdProperties;
import io.github.dmitrykislov.contractfirst.client.RetryProperties;
import io.github.dmitrykislov.contractfirst.client.auth.AuthProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the Orders client that are not covered by Spring Boot's generic
 * {@code spring.http.serviceclient.orders.*} properties (base URL, timeouts, redirects, SSL, default
 * headers). The nested blocks come from {@code contract-first-client-support} and are shared by every
 * contract client in an application:
 *
 * <pre>
 * orders.client.enabled=true
 * orders.client.auth.mode=static            # static | propagate | provider
 * orders.client.auth.header-name=X-API-Key
 * orders.client.auth.scheme=                # e.g. Bearer when the header is Authorization
 * orders.client.auth.token=...              # required in static mode
 * orders.client.retry.enabled=true          # idempotent calls only; see RetryProperties
 * orders.client.retry.max-attempts=3
 * orders.client.request-id.enabled=true     # copy the MDC request id onto X-Request-Id
 * </pre>
 *
 * @param enabled whether the client beans are registered at all (default {@code true})
 * @param auth how the token for the contract's {@code ApiKeyAuth} scheme is obtained
 * @param retry retries for idempotent calls
 * @param requestId correlation-id propagation
 */
@ConfigurationProperties(prefix = OrdersClientProperties.PREFIX)
public record OrdersClientProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue AuthProperties auth,
        @DefaultValue RetryProperties retry,
        @DefaultValue RequestIdProperties requestId) {

    public static final String PREFIX = "orders.client";

    /** Name of the HTTP service group; keys {@code spring.http.serviceclient.<group>.*}. */
    public static final String GROUP = "orders";
}
