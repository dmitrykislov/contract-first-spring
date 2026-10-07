package io.github.dmitrykislov.orders.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** A server without API keys would reject every request; it must refuse to start instead. */
class OrdersServerPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void refusesToStartWithoutApiKeys() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("apiKeys"));
        runner.withPropertyValues("orders.server.security.api-keys=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsConfiguredKeysAndBasePath() {
        runner.withPropertyValues("orders.server.security.api-keys=a,b", "openapi.orders.base-path=/v2")
                .run(context -> {
                    assertThat(context.getBean(OrdersServerProperties.class).security().apiKeys()).containsExactlyInAnyOrder("a", "b");
                    ApiPathProperties path = context.getBean(ApiPathProperties.class);
                    assertThat(path.covers("/v2/orders")).isTrue();
                    assertThat(path.covers("/v2")).isTrue();
                    assertThat(path.covers("/v20/orders")).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({OrdersServerProperties.class, ApiPathProperties.class})
    static class Config {}
}
