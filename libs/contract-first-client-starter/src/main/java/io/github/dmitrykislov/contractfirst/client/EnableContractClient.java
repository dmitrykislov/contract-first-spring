package io.github.dmitrykislov.contractfirst.client;

import io.github.dmitrykislov.contractfirst.client.autoconfigure.ContractClientBeanRegistrar;
import io.github.dmitrykislov.contractfirst.client.autoconfigure.HttpServiceGroupRegistrar;
import io.github.dmitrykislov.contractfirst.client.errors.ApiException;
import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Turns the generated {@code @HttpExchange} interfaces of one contract into a fully configured Spring
 * Boot HTTP service group. One annotated class per API is all the code a client module needs:
 *
 * <pre>
 * &#64;EnableContractClient(group = "orders", basePackageClasses = OrdersApi.class,
 *                       exception = OrdersApiException.class, tokenProvider = OrdersTokenProvider.class)
 * public class OrdersClient {}
 * </pre>
 *
 * <p>Registered under {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * in the client jar, so any Boot application that depends on it gets the clients; or placed in an
 * application's own configuration, where it is picked up by component scanning.
 *
 * <p>What it wires, for the group:
 * <ul>
 *   <li>the interfaces as HTTP service proxies (Spring Boot binds {@code spring.http.serviceclient.<group>.*});</li>
 *   <li>settings from {@code openapi.clients.groups.<group>.*} over {@code openapi.clients.defaults.*};</li>
 *   <li>token resolution: a bean of {@link #tokenProvider()} type if given, else a bean named
 *       {@code <group>TokenProvider}, else the built-in provider for the configured mode;</li>
 *   <li>retries, request-id propagation, contract-safe JSON and {@link ApiException} mapping.</li>
 * </ul>
 * {@code openapi.clients.groups.<group>.enabled=false} switches the whole registration off.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(HttpServiceClientAutoConfiguration.class)
@Import({HttpServiceGroupRegistrar.class, ContractClientBeanRegistrar.class})
public @interface EnableContractClient {

    /** HTTP service group name; also the key under {@code spring.http.serviceclient} and {@code openapi.clients}. */
    String group();

    /** Packages to scan for {@code @HttpExchange} interfaces, named by a class they contain. */
    Class<?>[] basePackageClasses() default {};

    /** Packages to scan for {@code @HttpExchange} interfaces, by name. */
    String[] basePackages() default {};

    /** Explicit interfaces to register instead of, or in addition to, scanning. */
    Class<?>[] types() default {};

    /** Exception thrown for 4xx/5xx; a subclass must expose the five-argument {@link ApiException} constructor. */
    Class<? extends ApiException> exception() default ApiException.class;

    /** Marker subtype whose unique bean supplies tokens; {@link TokenProvider} itself means "look up by name". */
    Class<? extends TokenProvider> tokenProvider() default TokenProvider.class;
}
