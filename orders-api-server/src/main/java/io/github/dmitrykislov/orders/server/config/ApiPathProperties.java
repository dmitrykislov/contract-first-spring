package io.github.dmitrykislov.orders.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The servlet path prefix under which the generated API interfaces are mounted. The generator derives
 * the default from {@code servers[0].url} in the contract and reads the same {@code openapi.orders.base-path}
 * property from its {@code @RequestMapping} placeholder, so filters and controllers always agree.
 *
 * @param basePath prefix without trailing slash, e.g. {@code /api/v1}
 */
@ConfigurationProperties(prefix = "openapi.orders")
public record ApiPathProperties(@DefaultValue("/api/v1") String basePath) {

    public boolean covers(String requestUri) {
        return requestUri.equals(basePath) || requestUri.startsWith(basePath + "/");
    }
}
