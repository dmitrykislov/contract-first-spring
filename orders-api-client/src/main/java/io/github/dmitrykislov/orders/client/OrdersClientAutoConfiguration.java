package io.github.dmitrykislov.orders.client;

import io.github.dmitrykislov.orders.client.api.OrdersApi;
import io.github.dmitrykislov.orders.client.auth.OrdersTokenProvider;
import io.github.dmitrykislov.orders.client.auth.PropagatingTokenProvider;
import io.github.dmitrykislov.orders.client.auth.StaticTokenProvider;
import io.github.dmitrykislov.orders.client.auth.TokenHeaderInterceptor;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers every generated {@code @HttpExchange} interface of the Orders API as a Spring HTTP
 * service client in the {@value OrdersClientProperties#GROUP} group.
 *
 * <p>Connection settings are plain Spring Boot properties and need no code here:
 * <pre>
 * spring.http.serviceclient.orders.base-url=https://orders.example.com/api/v1
 * spring.http.serviceclient.orders.connect-timeout=2s
 * spring.http.serviceclient.orders.read-timeout=5s
 * spring.http.serviceclient.orders.redirects=dont-follow
 * spring.http.serviceclient.orders.ssl.bundle=orders
 * </pre>
 *
 * <p>Authentication is governed by {@code orders.client.auth.*}; see {@link OrdersClientProperties.Mode}.
 *
 * <p>This configuration runs before {@link HttpServiceClientAutoConfiguration}, which is conditional
 * on the {@code HttpServiceProxyRegistry} bean that {@link ImportHttpServices} contributes.
 */
@AutoConfiguration(before = HttpServiceClientAutoConfiguration.class)
@ConditionalOnProperty(prefix = OrdersClientProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OrdersClientProperties.class)
@ImportHttpServices(group = OrdersClientProperties.GROUP, basePackageClasses = OrdersApi.class)
public class OrdersClientAutoConfiguration {

    /**
     * The built-in token source for {@code static} and {@code propagate} modes. Applications may
     * override it with their own bean in any mode; in {@code provider} mode they must.
     */
    @Bean
    @ConditionalOnMissingBean
    OrdersTokenProvider ordersTokenProvider(OrdersClientProperties properties) {
        OrdersClientProperties.Auth auth = properties.auth();
        return switch (auth.mode()) {
            case STATIC -> new StaticTokenProvider(Objects.requireNonNull(auth.token(), "validated by Auth"));
            case PROPAGATE -> new PropagatingTokenProvider(auth);
            case PROVIDER -> throw new IllegalStateException(
                    "orders.client.auth.mode=provider requires a bean of type " + OrdersTokenProvider.class.getName()
                            + " that fetches the token, e.g. from your identity provider");
        };
    }

    /**
     * Lets the consumer's own {@code JsonMapper} read and write the generated models too (logging,
     * caching, tests), since {@code nullable: true} properties are generated as {@code JsonNullable}.
     */
    @Bean
    @ConditionalOnMissingBean(JsonNullableJackson3Module.class)
    JsonNullableJackson3Module ordersClientJsonNullableModule() {
        return new JsonNullableJackson3Module();
    }

    /**
     * Adds per-request token resolution, contract-safe JSON and problem-aware error handling to every
     * client in the group. Runs after Boot's property-driven configurer (base URL, timeouts) and after
     * {@code RestClientCustomizer}s so that application code can still override individual settings.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    RestClientHttpServiceGroupConfigurer ordersClientGroupConfigurer(OrdersClientProperties properties,
            OrdersTokenProvider tokenProvider, JsonMapper jsonMapper) {
        // Optional properties in the contract are not nullable, so an unset field must be omitted rather
        // than sent as null. Derive a mapper from the application's one so other customisations survive,
        // but pin the inclusion policy regardless of the consumer's global Jackson settings.
        JsonMapper contractMapper = jsonMapper.rebuild()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(new JsonNullableJackson3Module()) // nullable contract properties are JsonNullable
                .build();
        ProblemResponseErrorHandler errorHandler = new ProblemResponseErrorHandler(contractMapper);
        TokenHeaderInterceptor tokenInterceptor = new TokenHeaderInterceptor(tokenProvider, properties.auth());
        return groups -> groups.filterByName(OrdersClientProperties.GROUP)
                .forEachClient((group, builder) -> builder
                        .configureMessageConverters(converters ->
                                converters.withJsonConverter(new JacksonJsonHttpMessageConverter(contractMapper)))
                        .requestInterceptor(tokenInterceptor)
                        .defaultStatusHandler(HttpStatusCode::isError, errorHandler));
    }
}
