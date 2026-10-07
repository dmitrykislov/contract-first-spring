package io.github.dmitrykislov.contractfirst.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.contractfirst.server.security.ApiKeyAuthenticationManager;
import io.github.dmitrykislov.contractfirst.server.security.ApiKeySecurity;
import io.github.dmitrykislov.contractfirst.server.security.ApiKeySecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.AuthenticationManager;
import tools.jackson.databind.json.JsonMapper;

class ContractServerAutoConfigurationTest {

    record Payload(String required, String optional, JsonNullable<String> nullable) {}

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    ContractServerAutoConfiguration.class, ApiKeySecurityAutoConfiguration.class));

    @Test
    void providesProblemJsonAndRequestIdBeansWithoutTouchingTheApplicationMapper() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ProblemFactory.class).hasSingleBean(ContractExceptionHandler.class)
                    .hasSingleBean(ServerHttpMessageConvertersCustomizer.class).hasSingleBean(ContractJson.class);
            assertThat(context.getBeansOfType(FilterRegistrationBean.class)).hasSize(1);

            JsonMapper application = context.getBean(JsonMapper.class);
            JsonMapper contract = context.getBean(ContractJson.class).mapper();
            Payload payload = new Payload("r", null, JsonNullable.undefined());
            assertThat(contract.writeValueAsString(payload)).isEqualTo("{\"required\":\"r\"}");
            assertThat(application.writeValueAsString(payload)).contains("\"optional\":null"); // untouched
        });
    }

    @Test
    void apiKeyBeansExistOnlyWhenKeysAreConfigured() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AuthenticationManager.class).doesNotHaveBean(ApiKeySecurity.class));

        runner.withPropertyValues("contract-first.server.api-key.keys=k1", "contract-first.server.base-path=/api")
                .run(context -> assertThat(context).hasSingleBean(ApiKeyAuthenticationManager.class).hasSingleBean(ApiKeySecurity.class));

        runner.withPropertyValues("contract-first.server.api-key.keys=")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("at least one key"));
    }

    @Test
    void requestIdFilterCanBeSwitchedOff() {
        runner.withPropertyValues("contract-first.server.request-id.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(FilterRegistrationBean.class)).isEmpty());
    }
}
