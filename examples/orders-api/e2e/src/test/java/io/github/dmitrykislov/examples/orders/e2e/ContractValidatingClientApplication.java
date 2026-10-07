package io.github.dmitrykislov.examples.orders.e2e;

import io.github.dmitrykislov.examples.orders.client.OrdersClient;
import io.github.dmitrykislov.contractfirst.testing.Contract;
import io.github.dmitrykislov.examples.orders.spec.OrdersContract;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * A minimal consumer application for the generated client. Everything comes from auto-configuration
 * and properties; the one addition is an interceptor that validates each real HTTP exchange against
 * the OpenAPI contract and fails the call when either side deviates from it.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class ContractValidatingClientApplication {

    @Bean
    RestClientHttpServiceGroupConfigurer contractValidation() {
        return groups -> groups.filterByName(OrdersClient.GROUP)
                .forEachClient((group, builder) -> builder.requestInterceptor(
                        Contract.fromClasspath(OrdersContract.RESOURCE, OrdersContract.BASE_PATH).validatingInterceptor()));
    }
}
