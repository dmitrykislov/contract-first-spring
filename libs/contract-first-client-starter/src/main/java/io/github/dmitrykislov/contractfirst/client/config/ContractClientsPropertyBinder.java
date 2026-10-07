package io.github.dmitrykislov.contractfirst.client.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Binds the {@code openapi.clients} namespace, which holds the settings of every contract client in an
 * application, keyed by HTTP service group exactly like Spring Boot's own
 * {@code spring.http.serviceclient.<group>}:
 *
 * <pre>
 * openapi.clients.defaults.retry.max-attempts=5          # applies to every group
 * openapi.clients.groups.orders.auth.token=${ORDERS_KEY} # the "orders" group
 * openapi.clients.groups.billing.auth.mode=propagate     # the "billing" group
 * openapi.clients.fail-on-unknown-group=true             # startup failure for a group nobody registered
 * </pre>
 *
 * <p>Group settings are layered over {@code defaults}. Records with {@code @DefaultValue} cannot be merged
 * after binding, so the raw keys are merged first and bound once per group, which is why this is not a
 * plain {@code @ConfigurationProperties} map.
 */
public final class ContractClientsPropertyBinder {

    public static final String PREFIX = "openapi.clients";
    public static final String GROUPS = "groups";
    public static final String DEFAULTS = "defaults";
    public static final String FAIL_ON_UNKNOWN_GROUP = "fail-on-unknown-group";

    private final Map<String, String> raw;

    public ContractClientsPropertyBinder(Environment environment) {
        BindResult<Map<String, String>> bound = Binder.get(environment).bind(PREFIX, Bindable.mapOf(String.class, String.class));
        this.raw = bound.orElse(Map.of());
    }

    /** The property prefix of one group, e.g. {@code openapi.clients.groups.orders}. */
    public static String groupPrefix(String group) {
        return PREFIX + "." + GROUPS + "." + group;
    }

    /** Settings for one group: defaults first, then the group's own keys, then the records' built-in defaults. */
    public ContractClientProperties forGroup(String group) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(keysUnder(DEFAULTS));
        merged.putAll(keysUnder(GROUPS + "." + group));
        try {
            return new Binder(new MapConfigurationPropertySource(merged))
                    .bindOrCreate("client", Bindable.of(ContractClientProperties.class));
        } catch (RuntimeException e) {
            // No cause on purpose: the group-qualified message must be the root cause a failure report shows.
            throw new IllegalStateException(groupPrefix(group) + ": " + rootMessage(e));
        }
    }

    /** Groups that have at least one property under {@code groups.<name>} and are not disabled. */
    public Set<String> configuredGroups() {
        Set<String> groups = new TreeSet<>();
        String prefix = GROUPS + ".";
        for (String key : raw.keySet()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String rest = key.substring(prefix.length());
            String group = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
            if (isEnabled(group)) {
                groups.add(group);
            }
        }
        return groups;
    }

    /** {@code openapi.clients.groups.<group>.enabled}, default {@code true}. */
    public boolean isEnabled(String group) {
        return !"false".equalsIgnoreCase(raw.getOrDefault(GROUPS + "." + group + ".enabled", "true"));
    }

    /** Whether a configured group without a registered client fails startup (default {@code true}). */
    public boolean failOnUnknownGroup() {
        return !"false".equalsIgnoreCase(raw.getOrDefault(FAIL_ON_UNKNOWN_GROUP, "true"));
    }

    private Map<String, Object> keysUnder(String head) {
        Map<String, Object> result = new TreeMap<>();
        String prefix = head + ".";
        raw.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                result.put("client." + key.substring(prefix.length()), value);
            }
        });
        return result;
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : t.getMessage();
    }
}
