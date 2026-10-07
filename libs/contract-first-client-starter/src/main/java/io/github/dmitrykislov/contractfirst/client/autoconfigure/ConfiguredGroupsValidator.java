package io.github.dmitrykislov.contractfirst.client.autoconfigure;

import io.github.dmitrykislov.contractfirst.client.ContractClientRegistration;
import io.github.dmitrykislov.contractfirst.client.EnableContractClient;
import io.github.dmitrykislov.contractfirst.client.config.ContractClientsPropertyBinder;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Fails startup when {@code openapi.clients.groups.<group>.*} is configured for a group no
 * {@link EnableContractClient} registered: almost always a misspelt group name that would otherwise be
 * silently ignored. Set {@code openapi.clients.fail-on-unknown-group=false} to log instead.
 */
final class ConfiguredGroupsValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ConfiguredGroupsValidator.class);

    private final ContractClientsPropertyBinder properties;
    private final ObjectProvider<ContractClientRegistration> registrations;

    ConfiguredGroupsValidator(ContractClientsPropertyBinder properties, ObjectProvider<ContractClientRegistration> registrations) {
        this.properties = properties;
        this.registrations = registrations;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> registered = new TreeSet<>();
        registrations.forEach(registration -> registered.add(registration.group()));
        Set<String> unknown = new TreeSet<>(properties.configuredGroups());
        unknown.removeAll(registered);
        if (unknown.isEmpty()) {
            return;
        }
        String message = "%s.%s.* is configured for %s but no @EnableContractClient registered such a group (registered: %s)"
                .formatted(ContractClientsPropertyBinder.PREFIX, ContractClientsPropertyBinder.GROUPS, unknown, registered);
        if (properties.failOnUnknownGroup()) {
            throw new IllegalStateException(message + "; fix the group name or set " + ContractClientsPropertyBinder.PREFIX + "."
                    + ContractClientsPropertyBinder.FAIL_ON_UNKNOWN_GROUP + "=false");
        }
        log.warn(message);
    }
}
