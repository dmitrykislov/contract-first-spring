package io.github.dmitrykislov.contractfirst.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ContractServerPropertiesTest {

    @Test
    void basePathIsNormalisedAndCoversOnlyItsSubtree() {
        ContractServerProperties.ApiKey noKeys = new ContractServerProperties.ApiKey("X-API-Key", null);
        ContractServerProperties p = new ContractServerProperties("/api/v1/", new ContractServerProperties.Problem("urn:problem-type:"),
                new ContractServerProperties.RequestId(true, "X-Request-Id", "requestId"), noKeys);
        assertThat(p.basePath()).isEqualTo("/api/v1");
        assertThat(p.covers("/api/v1")).isTrue();
        assertThat(p.covers("/api/v1/orders")).isTrue();
        assertThat(p.covers("/api/v10/orders")).isFalse();
        assertThat(noKeys.configured()).isFalse();

        ContractServerProperties root = new ContractServerProperties("", null, null, null);
        assertThat(root.basePath()).isEqualTo("/");
        assertThat(root.covers("/anything")).isTrue();
    }

    @Test
    void bindsWithDefaultsAndExplicitValues() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("openapi.server.base-path=/v2", "openapi.server.api-key.accepted-keys=a,b",
                        "openapi.server.problem.type-namespace=https://d.test/p/")
                .run(context -> {
                    ContractServerProperties p = context.getBean(ContractServerProperties.class);
                    assertThat(p.basePath()).isEqualTo("/v2");
                    assertThat(p.apiKey().acceptedKeys()).containsExactlyInAnyOrder("a", "b");
                    assertThat(p.apiKey().headerName()).isEqualTo("X-API-Key");
                    assertThat(p.requestId().enabled()).isTrue();
                    assertThat(p.problem().typeNamespace()).isEqualTo("https://d.test/p/");
                });
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .run(context -> assertThat(context.getBean(ContractServerProperties.class).apiKey().acceptedKeys()).isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ContractServerProperties.class)
    static class Config {}
}
