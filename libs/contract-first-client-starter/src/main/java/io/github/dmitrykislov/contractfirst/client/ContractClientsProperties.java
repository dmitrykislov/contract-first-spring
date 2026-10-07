package io.github.dmitrykislov.contractfirst.client;

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
 * One namespace for every contract client, keyed by HTTP service group exactly like Spring Boot's own
 * {@code spring.http.serviceclient.<group>}:
 *
 * <pre>
 * contract-first.clients.defaults.retry.max-attempts=5        # applies to every client
 * contract-first.clients.orders.auth.token=${ORDERS_API_KEY}  # the "orders" group
 * contract-first.clients.billing.auth.mode=propagate          # the "billing" group
 * contract-first.clients.strict=true                          # fail startup on a group nobody registered
 * </pre>
 *
 * <p>Group settings are layered over {@code defaults}, which is why this is not a plain
 * {@code @ConfigurationProperties} map: records with {@code @DefaultValue} cannot be merged after
 * binding, so the raw keys are merged first and bound once.
 */
public final class ContractClientsProperties {

    public static final String PREFIX = "contract-first.clients";
    public static final String DEFAULTS_KEY = "defaults";
    private static final String STRICT_KEY = "strict";

    private final Map<String, String> raw;

    public ContractClientsProperties(Environment environment) {
        BindResult<Map<String, String>> bound = Binder.get(environment).bind(PREFIX, Bindable.mapOf(String.class, String.class));
        this.raw = bound.orElse(Map.of());
    }

    /** Settings for one group: defaults first, then the group's own keys, then record defaults for the rest. */
    public ClientSettings settingsFor(String group) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(keysUnder(DEFAULTS_KEY));
        merged.putAll(keysUnder(group));
        try {
            return new Binder(new MapConfigurationPropertySource(merged))
                    .bindOrCreate("client", Bindable.of(ClientSettings.class));
        } catch (RuntimeException e) {
            // No cause on purpose: the group-qualified message must be the root cause a failure report shows.
            throw new IllegalStateException(PREFIX + "." + group + ": " + rootMessage(e));
        }
    }

    /** Groups that have at least one property configured and are not disabled, excluding {@code defaults}. */
    public Set<String> configuredGroups() {
        Set<String> groups = new TreeSet<>();
        for (String key : raw.keySet()) {
            String head = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
            if (!DEFAULTS_KEY.equals(head) && !STRICT_KEY.equals(head) && isEnabled(head)) {
                groups.add(head);
            }
        }
        return groups;
    }

    /** {@code contract-first.clients.<group>.enabled}, default {@code true}. */
    public boolean isEnabled(String group) {
        return !"false".equalsIgnoreCase(raw.getOrDefault(group + ".enabled", "true"));
    }

    /** Whether a configured group without a registered client should fail startup (default {@code true}). */
    public boolean strict() {
        return !"false".equalsIgnoreCase(raw.getOrDefault(STRICT_KEY, "true"));
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
