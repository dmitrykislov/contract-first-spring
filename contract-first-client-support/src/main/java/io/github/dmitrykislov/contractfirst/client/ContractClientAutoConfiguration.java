package io.github.dmitrykislov.contractfirst.client;

import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Lets the consumer's own {@code JsonMapper} read and write generated models (logging, caching, tests),
 * since {@code nullable: true} properties are generated as {@code JsonNullable}. Spring Boot registers
 * every {@code JacksonModule} bean on its mapper.
 */
@AutoConfiguration
public class ContractClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JsonNullableJackson3Module.class)
    JsonNullableJackson3Module jsonNullableJackson3Module() {
        return new JsonNullableJackson3Module();
    }
}
