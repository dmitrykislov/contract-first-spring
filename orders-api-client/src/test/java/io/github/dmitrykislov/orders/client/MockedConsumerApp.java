package io.github.dmitrykislov.orders.client;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * Stands in for an application that depends on {@code orders-api-client}. Auto-configuration does the
 * registration; the only test-specific bean swaps the HTTP layer for a {@link MockRestServiceServer},
 * running after every other configurer so it wins.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class MockedConsumerApp {

    /** Holds the mock server that is bound to the group's RestClient.Builder during group configuration. */
    public static final class MockServerHolder {
        private final AtomicReference<MockRestServiceServer> server = new AtomicReference<>();

        public MockRestServiceServer get() {
            return server.get();
        }
    }

    @Bean
    MockServerHolder mockServerHolder() {
        return new MockServerHolder();
    }

    @Bean
    @org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
    RestClientHttpServiceGroupConfigurer mockServerBinding(MockServerHolder holder) {
        return groups -> groups.filterByName(OrdersClient.GROUP)
                .forEachClient((group, builder) -> holder.server.set(MockRestServiceServer.bindTo(builder).build()));
    }
}
