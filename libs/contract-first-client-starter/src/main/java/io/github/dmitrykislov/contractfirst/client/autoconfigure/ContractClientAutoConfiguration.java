package io.github.dmitrykislov.contractfirst.client.autoconfigure;

import io.github.dmitrykislov.contractfirst.client.ContractClientRegistration;
import io.github.dmitrykislov.contractfirst.client.config.ContractClientsPropertyBinder;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Shared beans for every contract client: the {@code openapi.clients.groups.*} settings, the startup
 * check for misspelt groups, and the Jackson module that lets the application mapper read generated
 * models with {@code JsonNullable} properties.
 */
@AutoConfiguration
public class ContractClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ContractClientsPropertyBinder contractClientsProperties(Environment environment) {
        return new ContractClientsPropertyBinder(environment);
    }

    @Bean
    ConfiguredGroupsValidator contractClientsValidator(ContractClientsPropertyBinder properties,
            ObjectProvider<ContractClientRegistration> registrations) {
        return new ConfiguredGroupsValidator(properties, registrations);
    }

    @Bean
    @ConditionalOnMissingBean(JsonNullableJackson3Module.class)
    JsonNullableJackson3Module jsonNullableJackson3Module() {
        return new JsonNullableJackson3Module();
    }
}
