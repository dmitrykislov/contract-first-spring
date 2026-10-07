package io.github.dmitrykislov.orders.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Server-side settings that are not part of the contract.
 *
 * @param security API-key settings
 */
@Validated
@ConfigurationProperties(prefix = "orders.server")
public record OrdersServerProperties(@Valid @DefaultValue Security security) {

    /**
     * @param apiKeys the set of API keys accepted in the {@code X-API-Key} header; the application
     *                refuses to start when none is configured
     */
    public record Security(@NotEmpty Set<String> apiKeys) {}
}
