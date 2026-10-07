package io.github.dmitrykislov.contractfirst.client;

import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Shared beans for every contract client: the {@code contract-first.clients.*} settings, the startup
 * check for misspelt groups, and the Jackson module that lets the application mapper read generated
 * models with {@code JsonNullable} properties.
 */
@AutoConfiguration
public class ContractClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ContractClientsProperties contractClientsProperties(Environment environment) {
        return new ContractClientsProperties(environment);
    }

    @Bean
    ContractClientsValidator contractClientsValidator(ContractClientsProperties properties,
            ObjectProvider<ContractClientRegistration> registrations) {
        return new ContractClientsValidator(properties, registrations);
    }

    @Bean
    @ConditionalOnMissingBean(JsonNullableJackson3Module.class)
    JsonNullableJackson3Module jsonNullableJackson3Module() {
        return new JsonNullableJackson3Module();
    }
}
