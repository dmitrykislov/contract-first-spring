package io.github.dmitrykislov.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the auto-configured client end to end against a {@link MockRestServiceServer}: property
 * binding, URL building, headers, JSON (de)serialisation and problem-aware error mapping.
 */
@SpringBootTest(classes = MockedConsumerApp.class, properties = {
        "spring.http.serviceclient.orders.base-url=http://orders.test/api/v1",
        "spring.http.serviceclient.orders.connect-timeout=1500ms",
        "spring.http.serviceclient.orders.read-timeout=2500ms",
        "orders.client.auth.token=secret-key"
})
class OrdersClientAutoConfigurationTest {

    private static final String BASE = "http://orders.test/api/v1";

    @Autowired OrdersApi ordersApi;
    @Autowired CatalogApi catalogApi;
    @Autowired MockedConsumerApp.MockServerHolder mockServer;
    @Autowired JsonMapper json;
    @Autowired HttpServiceClientProperties serviceClientProperties;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = mockServer.get();
        server.reset();
    }

    @AfterEach
    void verifyAllExpectationsMet() {
        server.verify();
    }

    @Test
    void bindsConnectionSettingsFromStandardSpringBootProperties() {
        var group = serviceClientProperties.get(OrdersClientProperties.GROUP);
        assertThat(group.getBaseUrl()).isEqualTo(BASE);
        assertThat(group.getConnectTimeout()).isEqualTo(Duration.ofMillis(1500));
        assertThat(group.getReadTimeout()).isEqualTo(Duration.ofMillis(2500));
    }

    @Test
    void getOrderSendsApiKeyAndDecodesResponse() {
        UUID id = UUID.randomUUID();
        Order order = sampleOrder(id);
        server.expect(once(), requestTo(BASE + "/orders/" + id))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "secret-key"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/json, application/problem+json"))
                .andRespond(withSuccess(json.writeValueAsString(order), MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ETAG, "\"3\""));

        ResponseEntity<Order> response = ordersApi.getOrder(id, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(response.getBody()).isEqualTo(order);
    }

    @Test
    void listOrdersEncodesEveryQueryParameterKind() {
        UUID customer = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        // Zero seconds on purpose: OffsetDateTime.toString() would drop them and break RFC 3339.
        OffsetDateTime after = OffsetDateTime.of(2026, 1, 2, 3, 4, 0, 0, ZoneOffset.UTC);
        server.expect(once(), request -> assertThat(request.getURI().toString()).startsWith(BASE + "/orders?"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Request-Id", requestId.toString()))
                .andExpect(queryParam("status", "PENDING"))
                .andExpect(queryParam("customerId", customer.toString()))
                .andExpect(queryParam("tag", "gift", "priority"))
                .andExpect(queryParam("page", "2"))
                .andExpect(queryParam("size", "5"))
                .andExpect(request -> assertThat(request.getURI().getQuery()).contains("createdAfter=2026-01-02T03:04:00Z"))
                .andRespond(withSuccess(json.writeValueAsString(new OrderPage(List.of(), 2, 5, 0L)), MediaType.APPLICATION_JSON));

        ResponseEntity<OrderPage> response = ordersApi.listOrders(requestId, OrderStatus.PENDING, customer, after,
                List.of("gift", "priority"), 2, 5);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPage()).isEqualTo(2);
        assertThat(response.getBody().getItems()).isEmpty();
    }

    @Test
    void createOrderSendsHeadersAndJsonBodyAndExposesLocation() {
        UUID id = UUID.randomUUID();
        CreateOrderRequest request = sampleCreateRequest();
        server.expect(once(), requestTo(BASE + "/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "idem-12345678"))
                .andExpect(header("X-API-Key", "secret-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value(request.getCustomerId().toString()))
                .andExpect(jsonPath("$.lines[0].sku").value("WIDGET-BLUE-L"))
                .andExpect(jsonPath("$.lines[0].unitPrice.amount").value(19.99))
                .andExpect(jsonPath("$.shippingAddress.countryCode").value("US"))
                // unset optional fields must be omitted, not sent as null (the schema is not nullable)
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.tags").doesNotExist())
                .andExpect(jsonPath("$.shippingAddress.line2").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .location(URI.create(BASE + "/orders/" + id))
                        .body(json.writeValueAsString(sampleOrder(id))));

        ResponseEntity<Order> response = ordersApi.createOrder("idem-12345678", request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString(BASE + "/orders/" + id);
        assertThat(response.getBody()).isNotNull().extracting(Order::getId).isEqualTo(id);
    }

    @Test
    void deleteReturnsNoContent() {
        UUID id = UUID.randomUUID();
        server.expect(once(), request -> assertThat(request.getURI().toString()).isEqualTo(BASE + "/orders/" + id + "?reason=oops"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        ResponseEntity<Void> response = ordersApi.cancelOrder(id, null, "oops");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void catalogClientSendsHeaderParameterAndPathVariable() {
        server.expect(once(), requestTo(BASE + "/catalog/products/WIDGET-BLUE-L"))
                .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
                .andRespond(withSuccess("""
                        {"sku":"WIDGET-BLUE-L","name":"Großes blaues Widget","price":{"amount":19.99,"currency":"EUR"},"inStock":true}
                        """, MediaType.APPLICATION_JSON));

        var response = catalogApi.getProduct("WIDGET-BLUE-L", "de");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Großes blaues Widget");
    }

    @Test
    void problemResponsesBecomeTypedExceptionsCarryingTheProblem() {
        UUID id = UUID.randomUUID();
        server.expect(once(), requestTo(BASE + "/orders/" + id))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type":"https://orders.example.com/problems/not-found","title":"Not Found","status":404,
                                 "detail":"Order %s does not exist","instance":"http://orders.test/api/v1/orders/%s"}
                                """.formatted(id, id)));

        assertThatThrownBy(() -> ordersApi.getOrder(id, null))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.problem()).isPresent().get().satisfies(problem -> {
                        assertThat(problem.getType()).hasToString("https://orders.example.com/problems/not-found");
                        assertThat(problem.getStatus()).isEqualTo(404);
                        assertThat(problem.getDetail()).contains(id.toString());
                        assertThat(problem.getInstance()).hasToString("http://orders.test/api/v1/orders/" + id);
                    });
                })
                .hasMessageContaining("404 Not Found");
    }

    @Test
    void validationProblemsExposeFieldErrors() {
        server.expect(once(), requestTo(BASE + "/orders"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type":"https://orders.example.com/problems/validation-failed","title":"Validation failed","status":400,
                                 "errors":[{"field":"lines[0].quantity","message":"must be greater than or equal to 1","rejectedValue":"0"}]}
                                """));

        assertThatThrownBy(() -> ordersApi.createOrder("idem-12345678", sampleCreateRequest(), null))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                    assertThat(ex.fieldErrors()).singleElement().satisfies(error -> {
                        assertThat(error.getField()).isEqualTo("lines[0].quantity");
                        assertThat(error.getRejectedValue()).isEqualTo("0");
                    });
                    // the extension is also reachable generically through ProblemDetail
                    assertThat(ex.problem()).get().extracting(p -> p.getProperties().get("errors")).isNotNull();
                });
    }

    @Test
    void nonProblemErrorsStillRaiseTypedExceptionWithRawBody() {
        UUID id = UUID.randomUUID();
        server.expect(once(), requestTo(BASE + "/orders/" + id))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>upstream down</html>"));

        assertThatThrownBy(() -> ordersApi.getOrder(id, null))
                .isInstanceOfSatisfying(OrdersApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.problem()).isEmpty();
                })
                .hasMessageContaining("upstream down");
    }

    @Test
    void longNonProblemBodiesAreExcerptedInTheMessage() {
        UUID id = UUID.randomUUID();
        String hugeHtml = "<html>" + "x".repeat(5_000) + "</html>";
        server.expect(once(), requestTo(BASE + "/orders/" + id))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.TEXT_HTML).body(hugeHtml));

        assertThatThrownBy(() -> ordersApi.getOrder(id, null))
                .isInstanceOf(OrdersApiException.class)
                .hasMessageContaining("... (" + hugeHtml.length() + " chars)")
                .extracting(Throwable::getMessage).asString()
                .hasSizeLessThan(OrdersApiException.RAW_BODY_EXCERPT_LENGTH + 100);
    }

    @Test
    void patchSerialisesAbsentNullAndValueNotesDifferently() {
        UUID id = UUID.randomUUID();
        String orderJson = json.writeValueAsString(sampleOrder(id));
        server.expect(once(), requestTo(BASE + "/orders/" + id)).andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{}", true))
                .andRespond(withSuccess(orderJson, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/orders/" + id)).andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"notes\":null}", true))
                .andRespond(withSuccess(orderJson, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/orders/" + id)).andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"notes\":\"ring twice\",\"tags\":[]}", true))
                .andRespond(withSuccess(orderJson, MediaType.APPLICATION_JSON));

        ordersApi.patchOrder(id, new OrderPatch(), null);                                  // keep notes
        ordersApi.patchOrder(id, new OrderPatch().notes(null), null);                      // clear notes
        ordersApi.patchOrder(id, new OrderPatch().notes("ring twice").tags(Set.of()), null); // set notes, clear tags
    }

    @Test
    void clientCanBeSwitchedOffAndStaticModeRequiresAToken() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(MockedConsumerApp.class)
                .withPropertyValues("spring.http.serviceclient.orders.base-url=http://orders.test");

        runner.withPropertyValues("orders.client.auth.token=k", "orders.client.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OrdersApi.class));

        runner.withPropertyValues("orders.client.auth.token=k")
                .run(context -> assertThat(context).hasSingleBean(OrdersApi.class).hasSingleBean(CatalogApi.class));

        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("orders.client.auth.token"));
    }

    private static Order sampleOrder(UUID id) {
        OffsetDateTime now = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        return Order.builder()
                .id(id)
                .customerId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .lines(List.of(new OrderLine("WIDGET-BLUE-L", 2, new Money(new BigDecimal("19.99"), "EUR"))))
                .shippingAddress(new Address("1 Example Street", "Springfield", "12345", "US"))
                .tags(Set.of("gift"))
                .total(new Money(new BigDecimal("39.98"), "EUR"))
                .createdAt(now)
                .updatedAt(now)
                .version(1L)
                .build();
    }

    private static CreateOrderRequest sampleCreateRequest() {
        return new CreateOrderRequest(UUID.randomUUID(),
                List.of(new OrderLine("WIDGET-BLUE-L", 2, new Money(new BigDecimal("19.99"), "EUR"))),
                new Address("1 Example Street", "Springfield", "12345", "US"));
    }


}
