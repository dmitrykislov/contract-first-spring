package io.github.dmitrykislov.contractfirst.client;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Correlation-id propagation: copies the current request id from the logging MDC onto outgoing calls
 * so a chain of services shares one id. Designed to be nested under an API's own properties.
 *
 * @param enabled switch
 * @param headerName outgoing header, matching the contract's correlation header
 * @param mdcKey MDC key the server side puts the incoming id under
 */
public record RequestIdProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("X-Request-Id") String headerName,
        @DefaultValue("requestId") String mdcKey) {}
