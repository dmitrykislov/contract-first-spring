package io.github.dmitrykislov.orders.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dmitrykislov.orders.client.OrdersApiException;
import io.github.dmitrykislov.contractfirst.client.auth.ClientAuthenticationException;
import io.github.dmitrykislov.contractfirst.client.auth.TokenContext;
import io.github.dmitrykislov.contractfirst.testing.ContractValidatingInterceptor;
import io.github.dmitrykislov.orders.client.api.CatalogApi;
import io.github.dmitrykislov.orders.client.api.OrdersApi;
import io.github.dmitrykislov.orders.client.model.Address;
import io.github.dmitrykislov.orders.client.model.CreateOrderRequest;
import io.github.dmitrykislov.orders.client.model.Money;
import io.github.dmitrykislov.orders.client.model.Order;
import io.github.dmitrykislov.orders.client.model.OrderLine;
import io.github.dmitrykislov.orders.client.model.OrderPage;
import io.github.dmitrykislov.orders.client.model.OrderPatch;
import io.github.dmitrykislov.orders.client.model.OrderStatus;
import io.github.dmitrykislov.orders.client.model.Product;
import io.github.dmitrykislov.orders.client.model.UpdateOrderRequest;
import io.github.dmitrykislov.orders.server.OrdersServerApplication;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Boots the real server on a random port, then boots a separate consumer context whose only
 * knowledge of the server is the base URL and API key in properties, exactly like a deployed client.
 * Every call travels over HTTP through the generated declarative client and is validated against the
 * contract on the way out and on the way back in.
 */
@SpringBootTest(classes = OrdersServerApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "contract-first.server.api-key.keys=e2e-key",
        // The server JVM also has the client jar on its classpath; keep its context free of client beans.
        "spring.autoconfigure.exclude=io.github.dmitrykislov.orders.client.OrdersClient"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrdersEndToEndIT {

    private static final UUID CUSTOMER = UUID.fromString("5e1c3a0e-7d8f-4c1b-9a2d-3f4e5b6c7d8e");

    @LocalServerPort
    int port;

    private ConfigurableApplicationContext clientContext;
    private OrdersApi orders;
    private CatalogApi catalog;

    @BeforeAll
    void startConsumer() {
        clientContext = consumer("e2e-key");
        orders = clientContext.getBean(OrdersApi.class);
        catalog = clientContext.getBean(CatalogApi.class);
    }

    @AfterAll
    void stopConsumer() {
        clientContext.close();
    }

    private ConfigurableApplicationContext consumer(String apiKey) {
        return consumer(ContractValidatingClientApplication.class, apiKey);
    }

    private ConfigurableApplicationContext consumer(Class<?> application, String apiKey) {
        return new SpringApplicationBuilder(application)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.http.serviceclient.orders.base-url=http://localhost:" + port + "/api/v1",
                        "spring.http.serviceclient.orders.connect-timeout=2s",
                        "spring.http.serviceclient.orders.read-timeout=5s",
                        "contract-first.clients.orders.auth.token=" + apiKey)
                .run();
    }

    @Test
    @DisplayName("full order lifecycle through the typed client")
    void orderLifecycle() {
        UUID requestId = UUID.randomUUID();

        String idempotencyKey = "idem-" + UUID.randomUUID();
        ResponseEntity<Order> created = orders.createOrder(idempotencyKey, createRequest(), requestId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Order> replayed = orders.createOrder(idempotencyKey, createRequest(), null);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replayed.getBody()).isEqualTo(created.getBody());
        assertThat(created.getHeaders().getFirst("X-Request-Id")).isEqualTo(requestId.toString());
        Order order = created.getBody();
        assertThat(order).isNotNull();
        assertThat(created.getHeaders().getLocation()).hasToString("http://localhost:" + port + "/api/v1/orders/" + order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotal()).isEqualTo(new Money(new BigDecimal("49.48"), "EUR"));

        ResponseEntity<Order> fetched = orders.getOrder(order.getId(), null);
        assertThat(fetched.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(fetched.getBody()).isEqualTo(order);

        ResponseEntity<OrderPage> listed = orders.listOrders(null, OrderStatus.PENDING, CUSTOMER, OffsetDateTime.parse("2020-01-01T00:00:00Z"),
                List.of("gift"), 0, 50);
        assertThat(listed.getBody()).isNotNull();
        assertThat(listed.getBody().getItems()).extracting(Order::getId).contains(order.getId());

        ResponseEntity<Order> replaced = orders.replaceOrder(order.getId(), updateRequest(), null, fetched.getHeaders().getETag());
        assertThat(replaced.getBody()).isNotNull();
        assertThat(replaced.getBody().getVersion()).isEqualTo(2L);
        assertThat(replaced.getBody().getTotal().getAmount()).isEqualByComparingTo("149.00");

        ResponseEntity<Order> patched = orders.patchOrder(order.getId(), new OrderPatch().notes("Ring twice"), null);
        assertThat(patched.getBody()).isNotNull();
        assertThat(patched.getBody().getNotes()).isEqualTo("Ring twice");
        assertThat(patched.getBody().getTags()).containsExactly("replaced");
        assertThat(patched.getBody().getVersion()).isEqualTo(3L);

        // JSON Merge Patch semantics over the wire: an explicit null clears, absence keeps
        ResponseEntity<Order> clearedNotes = orders.patchOrder(order.getId(), new OrderPatch().notes(null), null);
        assertThat(clearedNotes.getBody()).isNotNull();
        assertThat(clearedNotes.getBody().getNotes()).isNull();
        assertThat(clearedNotes.getBody().getTags()).containsExactly("replaced");

        ResponseEntity<Order> submitted = orders.submitOrder(order.getId(), null);
        assertThat(submitted.getBody()).isNotNull();
        assertThat(submitted.getBody().getStatus()).isEqualTo(OrderStatus.SUBMITTED);

        ResponseEntity<Void> cancelled = orders.cancelOrder(order.getId(), null, "customer request");
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Order afterCancel = orders.getOrder(order.getId(), null).getBody();
        assertThat(afterCancel).isNotNull();
        assertThat(afterCancel.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(afterCancel.getCancellationReason()).isEqualTo("customer request");
    }

    @Test
    @DisplayName("business errors surface as typed exceptions with the server's Problem")
    void businessErrorsAreTyped() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> orders.getOrder(missing, null))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.problem()).get().satisfies(p -> {
                        assertThat(p.getType()).hasToString("https://orders.example.com/problems/not-found");
                        assertThat(p.getTitle()).isEqualTo("Not Found");
                        assertThat(p.getStatus()).isEqualTo(404);
                        assertThat(p.getDetail()).contains(missing.toString());
                        assertThat(p.getInstance()).hasToString("http://localhost:" + port + "/api/v1/orders/" + missing);
                    });
                });

        String key = "idem-" + UUID.randomUUID();
        Order order = orders.createOrder(key, createRequest(), null).getBody();
        assertThat(order).isNotNull();
        CreateOrderRequest differentPayload = createRequest().notes("not the same request");
        assertThatThrownBy(() -> orders.createOrder(key, differentPayload, null))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.problem()).get().extracting(ProblemDetail::getDetail).asString().contains("different payload");
                });

        CreateOrderRequest unknownSku = createRequest().lines(List.of(line("NO-SUCH-SKU", 1, "1.00")));
        assertThatThrownBy(() -> orders.createOrder("idem-" + UUID.randomUUID(), unknownSku, null))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(ex.problem()).get().extracting(ProblemDetail::getDetail).asString().contains("NO-SUCH-SKU");
                });

        assertThatThrownBy(() -> orders.replaceOrder(order.getId(), updateRequest(), null, "\"99\""))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED));

        orders.submitOrder(order.getId(), null);
        assertThatThrownBy(() -> orders.patchOrder(order.getId(), new OrderPatch().notes("late"), null))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("a wrong API key yields a spec-conformant 401 problem")
    void wrongApiKeyIsUnauthorized() {
        try (ConfigurableApplicationContext rogue = consumer("not-a-valid-key")) {
            OrdersApi rogueOrders = rogue.getBean(OrdersApi.class);
            assertThatThrownBy(() -> rogueOrders.listOrders(null, null, null, null, null, 0, 20))
                    .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                        assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(ex.problem()).get().extracting(ProblemDetail::getTitle).isEqualTo("Unauthorized");
                    });
        }
    }

    @Test
    @DisplayName("server-side validation failures carry field errors")
    void serverValidationFailuresCarryFieldErrors() {
        // The contract-validating consumer would refuse to send quantity 0 (schema minimum is 1), so use
        // a consumer without client-side validation to see how the server reports the violation.
        CreateOrderRequest badQuantity = createRequest().lines(List.of(line("WIDGET-BLUE-L", 0, "19.99")));
        try (ConfigurableApplicationContext plain = consumer(PlainClientApplication.class, "e2e-key")) {
            OrdersApi plainOrders = plain.getBean(OrdersApi.class);
            assertThatThrownBy(() -> plainOrders.createOrder("idem-" + UUID.randomUUID(), badQuantity, null))
                    .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.fieldErrors()).anySatisfy(e -> {
                            assertThat(e.field()).isEqualTo("lines[0].quantity");
                            assertThat(e.rejectedValue()).isEqualTo("0");
                        });
                    });
        }
    }

    @Test
    @DisplayName("a client bug that violates the contract is caught before the request leaves")
    void contractViolationsAreCaughtClientSide() {
        assertThatThrownBy(() -> orders.createOrder("short", createRequest(), null))
                .isInstanceOf(ContractValidatingInterceptor.ContractViolationException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    @DisplayName("the correlation id in the caller's MDC travels to the server and back")
    void requestIdFromMdcIsPropagatedEndToEnd() {
        UUID correlation = UUID.randomUUID();
        org.slf4j.MDC.put("requestId", correlation.toString());
        try {
            ResponseEntity<Product> response = catalog.getProduct("WIDGET-BLUE-L", "en");
            assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo(correlation.toString());
        } finally {
            org.slf4j.MDC.clear();
        }
    }

    @Test
    @DisplayName("catalog lookups honour header parameters")
    void catalogLookup() {
        ResponseEntity<Product> product = catalog.getProduct("WIDGET-RED-S", "de");
        assertThat(product.getBody()).isNotNull();
        assertThat(product.getBody().getName()).isEqualTo("Kleines rotes Widget");
        assertThat(product.getBody().getPrice()).isEqualTo(new Money(new BigDecimal("9.50"), "EUR"));

        assertThatThrownBy(() -> catalog.getProduct("GONE-1", "en"))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("propagate mode forwards the caller's token to the server")
    void propagateModeForwardsCallerToken() {
        try (ConfigurableApplicationContext propagating = new SpringApplicationBuilder(ContractValidatingClientApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.http.serviceclient.orders.base-url=http://localhost:" + port + "/api/v1",
                        "contract-first.clients.orders.auth.mode=propagate")
                .run()) {
            CatalogApi propagatingCatalog = propagating.getBean(CatalogApi.class);

            Product product = TokenContext.with("e2e-key", () -> propagatingCatalog.getProduct("GADGET-X1", "en").getBody());
            assertThat(product).isNotNull();
            assertThat(product.getInStock()).isFalse();

            assertThatThrownBy(() -> TokenContext.with("wrong", () -> propagatingCatalog.getProduct("GADGET-X1", "en")))
                    .isInstanceOfSatisfying(OrdersApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
            assertThatThrownBy(() -> propagatingCatalog.getProduct("GADGET-X1", "en"))
                    .isInstanceOf(ClientAuthenticationException.class);
        }
    }

    @Test
    @DisplayName("provider mode fetches the token on the fly from the application's provider")
    void providerModeFetchesTokenOnTheFly() {
        try (ConfigurableApplicationContext providing = new SpringApplicationBuilder(TokenFetchingClientApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.http.serviceclient.orders.base-url=http://localhost:" + port + "/api/v1",
                        "contract-first.clients.orders.auth.mode=provider",
                        "e2e.vault.token=e2e-key")
                .run()) {
            CatalogApi providingCatalog = providing.getBean(CatalogApi.class);
            TokenFetchingClientApplication.FakeVault vault = providing.getBean(TokenFetchingClientApplication.FakeVault.class);

            providingCatalog.getProduct("WIDGET-RED-S", "en");
            providingCatalog.getProduct("WIDGET-BLUE-L", "en");

            assertThat(vault.fetches()).isEqualTo(2);
        }
    }

    private static CreateOrderRequest createRequest() {
        return new CreateOrderRequest(CUSTOMER, List.of(line("WIDGET-BLUE-L", 2, "19.99"), line("WIDGET-RED-S", 1, "9.50")), address())
                .notes("Leave at the door")
                .tags(Set.of("gift", "priority"));
    }

    private static UpdateOrderRequest updateRequest() {
        return new UpdateOrderRequest(List.of(line("GADGET-X1", 1, "149.00")), address().line1("2 Replacement Road"))
                .tags(Set.of("replaced"));
    }

    private static OrderLine line(String sku, int quantity, String unitPrice) {
        return new OrderLine(sku, quantity, new Money(new BigDecimal(unitPrice), "EUR"));
    }

    private static Address address() {
        return new Address("1 Example Street", "Springfield", "12345", "US").line2("Suite 4");
    }
}
