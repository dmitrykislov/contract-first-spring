package io.github.dmitrykislov.contractfirst.server;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of a service that implements an OpenAPI contract, under {@code openapi.server}:
 *
 * <pre>
 * openapi.server.base-path=/api/v1                       # path part of servers[0].url
 * openapi.server.problem.type-namespace=https://api.example.com/problems/
 * openapi.server.request-id.header-name=X-Request-Id
 * openapi.server.api-key.accepted-keys=key-1,key-2       # presence switches the API-key security beans on
 * </pre>
 *
 * The generator mounts the generated interfaces under its own, title-derived placeholder
 * ({@code openapi.<title>.base-path}); point that at {@code ${openapi.server.base-path}} so both agree.
 *
 * @param basePath prefix under which the generated interfaces are mounted, without trailing slash
 * @param problem problem-detail settings
 * @param requestId correlation-id settings
 * @param apiKey API-key settings; the security beans exist only when {@code acceptedKeys} is non-empty
 */
@ConfigurationProperties(prefix = ContractServerProperties.PREFIX)
public record ContractServerProperties(
        @DefaultValue("/") String basePath,
        @DefaultValue Problem problem,
        @DefaultValue RequestId requestId,
        @DefaultValue ApiKey apiKey) {

    public static final String PREFIX = "openapi.server";

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
     * @param typeNamespace prefix of every problem {@code type} URI; a slug of the title is appended. Any URI
     *                      scheme works, e.g. {@code https://docs.example.com/problems/} or {@code urn:problem-type:}
     */
    public record Problem(@DefaultValue("urn:problem-type:") String typeNamespace) {}

    /**
     * @param enabled register the correlation filter
     * @param headerName correlation header, echoed back and generated when absent or not a UUID
     * @param mdcKey MDC key the id is published under (the client starter reads the same key)
     */
    public record RequestId(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("X-Request-Id") String headerName,
            @DefaultValue("requestId") String mdcKey) {}

    /**
     * @param headerName header carrying the key
     * @param acceptedKeys keys the server accepts; empty means the API-key security beans are not created
     */
    public record ApiKey(@DefaultValue("X-API-Key") String headerName, Set<String> acceptedKeys) {
        public ApiKey {
            acceptedKeys = acceptedKeys == null ? Set.of() : Set.copyOf(acceptedKeys);
        }

        public boolean configured() {
            return !acceptedKeys.isEmpty();
        }
    }
}
