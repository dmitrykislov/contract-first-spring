package io.github.dmitrykislov.contractfirst.client.autoconfigure;

import io.github.dmitrykislov.contractfirst.client.ContractClientRegistration;
import io.github.dmitrykislov.contractfirst.client.EnableContractClient;
import io.github.dmitrykislov.contractfirst.client.errors.ApiExceptionFactory;
import io.github.dmitrykislov.contractfirst.client.errors.ApiFieldError;
import io.github.dmitrykislov.contractfirst.client.errors.ApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.annotation.GetExchange;

/** The annotation on a hand-written {@code @HttpExchange} interface, without any generator involved. */
class EnableContractClientTest {

    interface ThingsApi {
        @GetExchange("/things/{id}")
        String thing(@PathVariable String id);
    }

    public static final class ThingsApiException extends ApiException {
        public ThingsApiException(String group, HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> errors, @Nullable String raw) {
            super(group, status, problem, errors, raw);
        }
    }

    static final class Holder {
        final AtomicReference<MockRestServiceServer> server = new AtomicReference<>();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableContractClient(group = "things", types = ThingsApi.class, exception = ThingsApiException.class)
    static class ConsumerApp {
        @Bean Holder holder() { return new Holder(); }

        @Bean @org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
        RestClientHttpServiceGroupConfigurer mock(Holder holder) {
            return groups -> groups.filterByName("things").forEachClient((g, b) -> holder.server.set(MockRestServiceServer.bindTo(b).build()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NamedProviderConfig {
        @Bean TokenProvider thingsTokenProvider() { return () -> Optional.of("from-named-bean"); }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApp.class)
            .withPropertyValues("spring.http.serviceclient.things.base-url=http://things.test/v1");

    @Test
    void registersTheProxyAppliesBootPropertiesTokenAndTypedErrors() {
        runner.withPropertyValues("openapi.clients.groups.things.auth.token=k1",
                        "openapi.clients.groups.things.auth.header-name=Authorization",
                        "openapi.clients.groups.things.auth.scheme=Bearer")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThingsApi.class).hasSingleBean(ContractClientRegistration.class);
                    MockRestServiceServer server = server(context);
                    server.expect(once(), requestTo("http://things.test/v1/things/1"))
                            .andExpect(header("Authorization", "Bearer k1"))
                            .andRespond(withSuccess("one", MediaType.TEXT_PLAIN));
                    server.expect(once(), requestTo("http://things.test/v1/things/2"))
                            .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                                    .body("{\"title\":\"Not Found\",\"status\":404,\"detail\":\"no 2\"}"));

                    ThingsApi api = context.getBean(ThingsApi.class);
                    assertThat(api.thing("1")).isEqualTo("one");
                    assertThatThrownBy(() -> api.thing("2")).isInstanceOfSatisfying(ThingsApiException.class, ex -> {
                        assertThat(ex.group()).isEqualTo("things");
                        assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    });
                    server.verify();
                });
    }

    @Test
    void providerModeUsesTheBeanNamedAfterTheGroupAndFailsWithoutOne() {
        runner.withPropertyValues("openapi.clients.groups.things.auth.mode=provider")
                .withUserConfiguration(NamedProviderConfig.class)
                .run(context -> {
                    server(context).expect(once(), requestTo("http://things.test/v1/things/1"))
                            .andExpect(header("X-API-Key", "from-named-bean"))
                            .andRespond(withSuccess("one", MediaType.TEXT_PLAIN));
                    context.getBean(ThingsApi.class).thing("1");
                    server(context).verify();
                });

        runner.withPropertyValues("openapi.clients.groups.things.auth.mode=provider")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("thingsTokenProvider").hasMessageContaining("openapi.clients.groups.things"));
    }

    @Test
    void defaultsAreLayeredUnderGroupSettings() {
        runner.withPropertyValues("openapi.clients.defaults.auth.token=shared",
                        "openapi.clients.defaults.auth.header-name=X-Shared",
                        "openapi.clients.groups.things.auth.header-name=X-Things")
                .run(context -> {
                    server(context).expect(once(), requestTo("http://things.test/v1/things/1"))
                            .andExpect(header("X-Things", "shared"))
                            .andRespond(withSuccess("one", MediaType.TEXT_PLAIN));
                    context.getBean(ThingsApi.class).thing("1");
                    server(context).verify();
                });
    }

    @Test
    void canBeDisabledRequiresATokenInStaticModeAndRejectsUnknownGroups() {
        runner.withPropertyValues("openapi.clients.groups.things.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ThingsApi.class));

        runner.run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("auth.token").hasMessageContaining("openapi.clients.groups.things"));

        runner.withPropertyValues("openapi.clients.groups.things.auth.token=k", "openapi.clients.groups.thngs.auth.token=k")
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("[thngs]"));
    }

    @Test
    void exceptionTypeIsValidatedAtStartup() {
        assertThatThrownBy(() -> ApiExceptionFactory.forType(NoCanonicalConstructor.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("five-argument".replace("five-argument", "constructor"));
    }

    static final class NoCanonicalConstructor extends ApiException {
        NoCanonicalConstructor() { super("x", HttpStatus.BAD_GATEWAY, null, List.of(), null); }
    }

    private static MockRestServiceServer server(AssertableApplicationContext context) {
        return context.getBean(Holder.class).server.get();
    }
}
