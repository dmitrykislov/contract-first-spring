package io.github.dmitrykislov.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.dmitrykislov.orders.client.api.CatalogApi;
import io.github.dmitrykislov.contractfirst.client.auth.ClientAuthenticationException;
import io.github.dmitrykislov.contractfirst.client.auth.TokenContext;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** How the client obtains the token in each {@code contract-first.clients.orders.auth.mode}. */
class TokenStrategiesTest {

    private static final String PRODUCT_URL = "http://orders.test/api/v1/catalog/products/WIDGET-BLUE-L";
    private static final String PRODUCT_JSON = """
            {"sku":"WIDGET-BLUE-L","name":"Large blue widget","price":{"amount":19.99,"currency":"EUR"},"inStock":true}
            """;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockedConsumerApp.class)
            .withPropertyValues("spring.http.serviceclient.orders.base-url=http://orders.test/api/v1");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class StaticMode {

        @Test
        void sendsConfiguredTokenWithOptionalSchemeOnConfiguredHeader() {
            runner.withPropertyValues(
                    "contract-first.clients.orders.auth.token=abc123",
                    "contract-first.clients.orders.auth.header-name=Authorization",
                    "contract-first.clients.orders.auth.scheme=Bearer")
                    .run(context -> {
                        expectProduct(context, header("Authorization", "Bearer abc123"), headerDoesNotExist("X-API-Key"));
                        assertThat(catalog(context).getProduct("WIDGET-BLUE-L", "en").getBody()).isNotNull();
                        server(context).verify();
                    });
        }
    }

    @Nested
    class PropagateMode {

        @Test
        void forwardsTokenBoundInTokenContext() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=propagate").run(context -> {
                expectProduct(context, header("X-API-Key", "caller-token"));

                var product = TokenContext.with("caller-token", () -> catalog(context).getProduct("WIDGET-BLUE-L", "en"));

                assertThat(product.getBody()).isNotNull();
                server(context).verify();
            });
        }

        @Test
        void forwardsHeaderOfTheServletRequestBeingHandled() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=propagate").run(context -> {
                MockHttpServletRequest incoming = new MockHttpServletRequest("GET", "/my-app/checkout");
                incoming.addHeader("X-API-Key", "forwarded-token");
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
                expectProduct(context, header("X-API-Key", "forwarded-token"));

                catalog(context).getProduct("WIDGET-BLUE-L", "en");

                server(context).verify();
            });
        }

        @Test
        void stripsTheSchemeFromAForwardedHeaderSoItIsNotPrefixedTwice() {
            runner.withPropertyValues(
                    "contract-first.clients.orders.auth.mode=propagate",
                    "contract-first.clients.orders.auth.header-name=Authorization",
                    "contract-first.clients.orders.auth.scheme=Bearer")
                    .run(context -> {
                        MockHttpServletRequest incoming = new MockHttpServletRequest();
                        incoming.addHeader("Authorization", "bearer forwarded-jwt");
                        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
                        expectProduct(context, header("Authorization", "Bearer forwarded-jwt"));

                        catalog(context).getProduct("WIDGET-BLUE-L", "en");

                        server(context).verify();
                    });
        }

        @Test
        void explicitContextWinsOverServletRequest() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=propagate").run(context -> {
                MockHttpServletRequest incoming = new MockHttpServletRequest();
                incoming.addHeader("X-API-Key", "forwarded-token");
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
                expectProduct(context, header("X-API-Key", "explicit-token"));

                TokenContext.run("explicit-token", () -> catalog(context).getProduct("WIDGET-BLUE-L", "en"));

                server(context).verify();
            });
        }

        @Test
        void failsBeforeSendingWhenNoCallerTokenIsAvailable() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=propagate").run(context -> {
                assertThatThrownBy(() -> catalog(context).getProduct("WIDGET-BLUE-L", "en"))
                        .isInstanceOf(ClientAuthenticationException.class)
                        .hasMessageContaining("PROPAGATE")
                        .hasMessageContaining("TokenContext");
                server(context).verify(); // nothing was expected, nothing must have been sent
            });
        }

        @Test
        void doesNotRequireAStaticToken() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=propagate")
                    .run(context -> assertThat(context).hasNotFailed().hasSingleBean(CatalogApi.class));
        }
    }

    @Nested
    class ProviderMode {

        @Test
        void asksTheApplicationProviderOnEveryCall() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=provider", "contract-first.clients.orders.auth.scheme=Bearer")
                    .withUserConfiguration(CountingProviderConfig.class)
                    .run(context -> {
                        CountingProviderConfig.calls.set(0);
                        MockRestServiceServer server = server(context);
                        server.expect(once(), requestTo(PRODUCT_URL)).andExpect(header("X-API-Key", "Bearer fetched-1"))
                                .andRespond(withSuccess(PRODUCT_JSON, MediaType.APPLICATION_JSON));
                        server.expect(once(), requestTo(PRODUCT_URL)).andExpect(header("X-API-Key", "Bearer fetched-2"))
                                .andRespond(withSuccess(PRODUCT_JSON, MediaType.APPLICATION_JSON));

                        catalog(context).getProduct("WIDGET-BLUE-L", "en");
                        catalog(context).getProduct("WIDGET-BLUE-L", "en");

                        server.verify();
                        assertThat(CountingProviderConfig.calls).hasValue(2);
                    });
        }

        @Test
        void refusesToStartWithoutAProviderBean() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=provider")
                    .run(context -> assertThat(context).hasFailed()
                            .getFailure().rootCause().hasMessageContaining(OrdersTokenProvider.class.getName()).hasMessageContaining("contract-first.clients.orders"));
        }

        @Test
        void emptyTokenFromProviderIsReportedClearly() {
            runner.withPropertyValues("contract-first.clients.orders.auth.mode=provider")
                    .withBean("emptyProvider", OrdersTokenProvider.class, () -> Optional::empty)
                    .run(context -> assertThatThrownBy(() -> catalog(context).getProduct("WIDGET-BLUE-L", "en"))
                            .isInstanceOf(ClientAuthenticationException.class)
                            .hasMessageContaining("empty token"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CountingProviderConfig {
        static final AtomicInteger calls = new AtomicInteger();

        @Bean
        OrdersTokenProvider fetchingProvider() {
            // Stands in for a call to an identity provider / secret manager on each request.
            return () -> Optional.of("fetched-" + calls.incrementAndGet());
        }
    }

    private static MockRestServiceServer server(AssertableApplicationContext context) {
        return context.getBean(MockedConsumerApp.MockServerHolder.class).get();
    }

    private static CatalogApi catalog(AssertableApplicationContext context) {
        return context.getBean(CatalogApi.class);
    }

    /** Expects exactly one product lookup carrying the given request properties and answers it. */
    private static void expectProduct(AssertableApplicationContext context, RequestMatcher... matchers) {
        ResponseActions actions = server(context).expect(once(), requestTo(PRODUCT_URL));
        for (RequestMatcher matcher : matchers) {
            actions = actions.andExpect(matcher);
        }
        actions.andRespond(withSuccess(PRODUCT_JSON, MediaType.APPLICATION_JSON));
    }
}
