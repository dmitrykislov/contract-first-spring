package io.github.dmitrykislov.contractfirst.server;

import java.util.List;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Server-side defaults for a contract-first API: problem rendering, JSON policy at the HTTP boundary,
 * correlation ids. Every bean backs off when the application defines its own.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ContractServerProperties.class)
public class ContractServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProblemFactory problemFactory(ContractServerProperties properties) {
        return new ProblemFactory(properties.problems().typeNamespace());
    }

    @Bean
    @ConditionalOnMissingBean
    ContractJson contractJson(JsonMapper applicationMapper) {
        return new ContractJson(applicationMapper);
    }

    /** Scopes the contract's JSON policy to Spring MVC's converters instead of the application-wide mapper. */
    @Bean
    ServerHttpMessageConvertersCustomizer contractJsonConverters(ContractJson contractJson) {
        return builder -> builder.withJsonConverter(new JacksonJsonHttpMessageConverter(contractJson.mapper()));
    }

    /** Lets the application mapper read generated models too (tests, logging); additive and harmless. */
    @Bean
    @ConditionalOnMissingBean(JsonNullableJackson3Module.class)
    JsonNullableJackson3Module jsonNullableJackson3Module() {
        return new JsonNullableJackson3Module();
    }

    @Bean
    @ConditionalOnMissingBean
    ContractExceptionHandler contractExceptionHandler(ProblemFactory problems, List<DomainExceptionMapper> mappers) {
        return new ContractExceptionHandler(problems, mappers);
    }

    @Bean
    @ConditionalOnBooleanProperty(name = ContractServerProperties.PREFIX + ".request-id.enabled", matchIfMissing = true)
    FilterRegistrationBean<RequestIdFilter> requestIdFilter(ContractServerProperties properties) {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(new RequestIdFilter(properties.requestId()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // before the security chain, so even 401s carry the id
        return registration;
    }
}
