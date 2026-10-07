package io.github.dmitrykislov.orders.server.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON settings the contract depends on, kept in code so that no profile or test property file can
 * switch them off:
 * <ul>
 *   <li>Optional properties such as {@code notes} or {@code detail} are not nullable in the schema, so
 *       an absent value must be omitted rather than serialised as {@code null}.</li>
 *   <li>Properties declared {@code nullable: true} (e.g. {@code OrderPatch.notes}) are generated as
 *       {@code JsonNullable} to distinguish "absent" from "explicitly null"; the module teaches Jackson
 *       that wrapper.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

    @Bean
    JsonMapperBuilderCustomizer contractJsonSettings() {
        return builder -> builder
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(new JsonNullableJackson3Module());
    }
}
