package io.github.dmitrykislov.contractfirst.server;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Server-side settings shared by every contract-first API.
 *
 * <pre>
 * contract-first.server.base-path=/api/v1                 # path part of servers[0].url; also feed it to openapi.&lt;name&gt;.base-path
 * contract-first.server.problems.type-namespace=https://api.example.com/problems/
 * contract-first.server.request-id.header-name=X-Request-Id
 * contract-first.server.api-key.keys=key-1,key-2          # presence switches the API-key building blocks on
 * </pre>
 *
 * @param basePath prefix under which the generated interfaces are mounted
 * @param problems problem-detail settings
 * @param requestId correlation-id settings
 * @param apiKey API-key settings; the security beans exist only when {@code keys} is configured
 */
@ConfigurationProperties(prefix = ContractServerProperties.PREFIX)
public record ContractServerProperties(
        @DefaultValue("/") String basePath,
        @DefaultValue Problems problems,
        @DefaultValue RequestId requestId,
        @DefaultValue ApiKey apiKey) {

    public static final String PREFIX = "contract-first.server";

    public ContractServerProperties {
        if (basePath == null || basePath.isBlank()) {
            basePath = "/";
        }
        if (basePath.length() > 1 && basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
    }

    /** Whether a request URI lies under the base path. */
    public boolean covers(String requestUri) {
        return "/".equals(basePath) || requestUri.equals(basePath) || requestUri.startsWith(basePath + "/");
    }

    /**
     * @param typeNamespace prefix of every problem {@code type} URI; a slug of the title is appended.
     *                      Any URI scheme works, e.g. {@code https://docs.example.com/problems/} or {@code urn:problem-type:}
     */
    public record Problems(@DefaultValue("urn:problem-type:") String typeNamespace) {}

    /**
     * @param enabled register the filter
     * @param headerName correlation header, echoed back and generated when absent or not a UUID
     * @param mdcKey MDC key the id is published under
     */
    public record RequestId(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("X-Request-Id") String headerName,
            @DefaultValue("requestId") String mdcKey) {}

    /**
     * @param headerName header carrying the key
     * @param keys accepted keys; empty means the API-key beans are not created
     */
    public record ApiKey(@DefaultValue("X-API-Key") String headerName, Set<String> keys) {
        public ApiKey {
            keys = keys == null ? Set.of() : Set.copyOf(keys);
        }

        public boolean configured() {
            return !keys.isEmpty();
        }
    }
}
