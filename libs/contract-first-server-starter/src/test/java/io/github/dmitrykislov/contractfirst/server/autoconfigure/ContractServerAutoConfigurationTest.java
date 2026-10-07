package io.github.dmitrykislov.contractfirst.server.autoconfigure;

import io.github.dmitrykislov.contractfirst.server.json.ContractJsonMapper;
import io.github.dmitrykislov.contractfirst.server.problem.ProblemDetailExceptionHandler;
import io.github.dmitrykislov.contractfirst.server.problem.ProblemFactory;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.contractfirst.server.security.ApiKeyAuthenticationManager;
import io.github.dmitrykislov.contractfirst.server.security.ApiKeySecurityConfigurer;
import io.github.dmitrykislov.contractfirst.server.autoconfigure.ApiKeySecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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
            assertThat(context).hasSingleBean(ProblemFactory.class).hasSingleBean(ProblemDetailExceptionHandler.class)
                    .hasSingleBean(ServerHttpMessageConvertersCustomizer.class).hasSingleBean(ContractJsonMapper.class);
            assertThat(context.getBeansOfType(FilterRegistrationBean.class)).hasSize(1);

            JsonMapper application = context.getBean(JsonMapper.class);
            JsonMapper contract = context.getBean(ContractJsonMapper.class).mapper();
            Payload payload = new Payload("r", null, JsonNullable.undefined());
            assertThat(contract.writeValueAsString(payload)).isEqualTo("{\"required\":\"r\"}");
            assertThat(application.writeValueAsString(payload)).contains("\"optional\":null"); // untouched
        });
    }

    @Test
    void apiKeyBeansExistOnlyWhenKeysAreConfigured() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AuthenticationManager.class).doesNotHaveBean(ApiKeySecurityConfigurer.class));

        runner.withPropertyValues("openapi.server.api-key.accepted-keys=k1", "openapi.server.base-path=/api")
                .run(context -> assertThat(context).hasSingleBean(ApiKeyAuthenticationManager.class).hasSingleBean(ApiKeySecurityConfigurer.class));

        // the YAML list form binds as indexed properties; it must switch the beans on just the same
        runner.withPropertyValues("openapi.server.api-key.accepted-keys[0]=k1", "openapi.server.api-key.accepted-keys[1]=k2")
                .run(context -> assertThat(context).hasSingleBean(ApiKeyAuthenticationManager.class).hasSingleBean(ApiKeySecurityConfigurer.class));

        runner.withPropertyValues("openapi.server.api-key.accepted-keys=")
                .run(context -> assertThat(context).doesNotHaveBean(ApiKeySecurityConfigurer.class));
    }

    @Test
    void nothingActivatesInANonWebContextEvenWithKeysConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        ContractServerAutoConfiguration.class, ApiKeySecurityAutoConfiguration.class))
                .withPropertyValues("openapi.server.api-key.accepted-keys[0]=k1")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(ApiKeySecurityConfigurer.class).doesNotHaveBean(ProblemFactory.class));
    }

    @Test
    void requestIdFilterCanBeSwitchedOff() {
        runner.withPropertyValues("openapi.server.request-id.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(FilterRegistrationBean.class)).isEmpty());
    }
}
