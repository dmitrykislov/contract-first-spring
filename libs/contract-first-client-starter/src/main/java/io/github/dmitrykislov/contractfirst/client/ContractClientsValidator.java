package io.github.dmitrykislov.contractfirst.client;

import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Fails startup when {@code contract-first.clients.<group>.*} is configured for a group no
 * {@link EnableContractClient} registered: almost always a misspelt group name that would otherwise be
 * silently ignored. Set {@code contract-first.clients.strict=false} to log instead.
 */
final class ContractClientsValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ContractClientsValidator.class);

    private final ContractClientsProperties properties;
    private final ObjectProvider<ContractClientRegistration> registrations;

    ContractClientsValidator(ContractClientsProperties properties, ObjectProvider<ContractClientRegistration> registrations) {
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
        String message = "%s.* is configured for %s but no @EnableContractClient registered such a group (registered: %s)"
                .formatted(ContractClientsProperties.PREFIX, unknown, registered);
        if (properties.strict()) {
            throw new IllegalStateException(message + "; fix the group name or set " + ContractClientsProperties.PREFIX + ".strict=false");
        }
        log.warn(message);
    }
}
