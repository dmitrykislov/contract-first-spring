package io.github.dmitrykislov.orders.client;

import io.github.dmitrykislov.contractfirst.client.ContractClientSupport;
import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;
import io.github.dmitrykislov.orders.client.api.OrdersApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers every generated {@code @HttpExchange} interface of the Orders API as a Spring HTTP
 * service client in the {@value OrdersClientProperties#GROUP} group and composes the shared runtime
 * from {@code contract-first-client-support} (token, retries, request id, JSON policy, errors).
 *
 * <p>Connection settings are plain Spring Boot properties: {@code spring.http.serviceclient.orders.*}.
 * This configuration runs before {@link HttpServiceClientAutoConfiguration}, which is conditional on
 * the {@code HttpServiceProxyRegistry} bean that {@link ImportHttpServices} contributes.
 */
@AutoConfiguration(before = HttpServiceClientAutoConfiguration.class)
@ConditionalOnProperty(prefix = OrdersClientProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OrdersClientProperties.class)
@ImportHttpServices(group = OrdersClientProperties.GROUP, basePackageClasses = OrdersApi.class)
public class OrdersClientAutoConfiguration {

    /** Built-in token source for {@code static} and {@code propagate}; applications may override it in any mode. */
    @Bean
    @ConditionalOnMissingBean
    OrdersTokenProvider ordersTokenProvider(OrdersClientProperties properties) {
        TokenProvider provider = ContractClientSupport.tokenProvider(properties.auth(), OrdersTokenProvider.class);
        return provider::token;
    }

    /** Runs after Boot's property-driven configurer and after {@code RestClientCustomizer}s, so applications can still override. */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    RestClientHttpServiceGroupConfigurer ordersClientGroupConfigurer(OrdersClientProperties properties,
            OrdersTokenProvider tokenProvider, JsonMapper jsonMapper) {
        return ContractClientSupport.forGroup(OrdersClientProperties.GROUP)
                .auth(properties.auth(), tokenProvider)
                .retry(properties.retry())
                .requestId(properties.requestId())
                .jsonMapper(jsonMapper)
                .exceptions(OrdersApiException::new)
                .groupConfigurer();
    }
}
