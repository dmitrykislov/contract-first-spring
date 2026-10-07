package io.github.dmitrykislov.orders.e2e;

import io.github.dmitrykislov.orders.client.OrdersClientProperties;
import io.github.dmitrykislov.orders.testsupport.ContractValidatingInterceptor;
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
        return groups -> groups.filterByName(OrdersClientProperties.GROUP)
                .forEachClient((group, builder) -> builder.requestInterceptor(new ContractValidatingInterceptor()));
    }
}
