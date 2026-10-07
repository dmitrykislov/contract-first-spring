package io.github.dmitrykislov.orders.client;

import io.github.dmitrykislov.contractfirst.client.EnableContractClient;
import io.github.dmitrykislov.orders.client.api.OrdersApi;

/**
 * The entire hand-written wiring of the Orders client. Listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so any
 * Spring Boot application that depends on this jar can inject the generated interfaces.
 *
 * <p>Configure with {@code spring.http.serviceclient.orders.*} (base URL, timeouts, TLS) and
 * {@code contract-first.clients.orders.*} (auth, retry, request-id); see the README.
 */
@EnableContractClient(
        group = OrdersClient.GROUP,
        basePackageClasses = OrdersApi.class,
        exception = OrdersApiException.class,
        tokenProvider = OrdersTokenProvider.class)
public class OrdersClient {

    /** Name of the HTTP service group; key under {@code spring.http.serviceclient} and {@code contract-first.clients}. */
    public static final String GROUP = "orders";
}
