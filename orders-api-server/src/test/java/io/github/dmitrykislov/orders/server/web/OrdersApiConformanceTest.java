package io.github.dmitrykislov.orders.server.web;

import static io.github.dmitrykislov.orders.testsupport.MockMvcContract.assertExchangeConforms;
import static io.github.dmitrykislov.orders.testsupport.MockMvcContract.assertResponseConforms;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.orders.server.domain.OrderService;
import io.github.dmitrykislov.orders.server.domain.OrderState;
import io.github.dmitrykislov.orders.server.model.CreateOrderRequest;
import io.github.dmitrykislov.orders.server.model.Order;
import io.github.dmitrykislov.orders.server.model.OrderPage;
import io.github.dmitrykislov.orders.server.model.Money;
import io.github.dmitrykislov.orders.server.model.OrderLine;
import io.github.dmitrykislov.orders.server.model.OrderPatch;
import io.github.dmitrykislov.orders.server.model.OrderStatus;
import io.github.dmitrykislov.orders.server.model.Problem;
import io.github.dmitrykislov.orders.server.model.UpdateOrderRequest;
import io.github.dmitrykislov.orders.server.support.ApiTestBase;
import io.github.dmitrykislov.orders.server.support.Fixtures;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Drives every Orders operation through MockMvc and asserts both the behaviour and that each
 * request/response pair conforms to the OpenAPI contract.
 */
class OrdersApiConformanceTest extends ApiTestBase {

    @Autowired
    OrderService orderService;

    @Nested
    @DisplayName("POST /orders")
    class CreateOrder {

        @Test
        void createsPendingOrderWithLocationAndTotal() {
            MvcTestResult result = mvc.post().uri(url("/orders"))
                    .headers(authenticated())
                    .header("Idempotency-Key", Fixtures.idempotencyKey())
                    .header("X-Request-Id", randomId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Fixtures.createOrderRequest()))
                    .exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.CREATED).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
            Order order = fromJson(result, Order.class);
            assertThat(result).headers().hasValue(HttpHeaders.LOCATION, "http://localhost" + url("/orders/" + order.getId()));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getVersion()).isEqualTo(1L);
            assertThat(order.getTotal().getAmount()).isEqualByComparingTo(new BigDecimal("49.48"));
            assertThat(order.getTotal().getCurrency()).isEqualTo("EUR");
            assertThat(order.getTags()).containsExactlyInAnyOrder("gift", "priority");
            assertThat(order.getCreatedAt()).isEqualTo(order.getUpdatedAt());
        }

        @Test
        void echoesRequestIdHeader() {
            UUID requestId = randomId();
            MvcTestResult result = mvc.post().uri(url("/orders"))
                    .headers(authenticated())
                    .header("Idempotency-Key", Fixtures.idempotencyKey())
                    .header("X-Request-Id", requestId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Fixtures.createOrderRequest()))
                    .exchange();

            assertExchangeConforms(result);
            assertThat(result).headers().hasValue("X-Request-Id", requestId.toString());
        }

        @Test
        void replaysIdenticalRequestWithTheSameKeyInsteadOfCreatingTwice() {
            String key = Fixtures.idempotencyKey();
            MvcTestResult first = mvc.post().uri(url("/orders")).headers(authenticated()).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.createOrderRequest())).exchange();

            MvcTestResult replay = mvc.post().uri(url("/orders")).headers(authenticated()).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.createOrderRequest())).exchange();

            assertExchangeConforms(replay);
            assertThat(replay).hasStatus(HttpStatus.CREATED);
            assertThat(fromJson(replay, Order.class)).isEqualTo(fromJson(first, Order.class));
            assertThat(replay.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(first.getResponse().getHeader(HttpHeaders.LOCATION));
        }

        @Test
        void rejectsReusedIdempotencyKeyWithDifferentPayloadWith409() {
            String key = Fixtures.idempotencyKey();
            mvc.post().uri(url("/orders")).headers(authenticated()).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.createOrderRequest())).exchange();

            MvcTestResult result = mvc.post().uri(url("/orders")).headers(authenticated()).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Fixtures.createOrderRequest().notes("a different payload"))).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            Problem problem = fromJson(result, Problem.class);
            assertThat(problem.getStatus()).isEqualTo(409);
            assertThat(problem.getDetail()).contains(key).contains("different payload");
        }

        @Test
        void rejectsMixedCurrenciesWith422() {
            CreateOrderRequest request = Fixtures.createOrderRequest().lines(List.of(
                    Fixtures.line("WIDGET-BLUE-L", 1, "19.99"),
                    new OrderLine("WIDGET-RED-S", 1, new Money(new BigDecimal("9.50"), "USD"))));

            MvcTestResult result = mvc.post().uri(url("/orders")).headers(authenticated())
                    .header("Idempotency-Key", Fixtures.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(request)).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(fromJson(result, Problem.class).getDetail()).contains("EUR").contains("USD");
        }

        @Test
        void rejectsUnknownSkuWith422() {
            CreateOrderRequest request = Fixtures.createOrderRequest().lines(List.of(Fixtures.line("NOPE-123", 1, "1.00")));

            MvcTestResult result = mvc.post().uri(url("/orders")).headers(authenticated())
                    .header("Idempotency-Key", Fixtures.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(request)).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(fromJson(result, Problem.class).getDetail()).contains("NOPE-123");
        }

        @Test
        void rejectsSchemaViolationsWithFieldErrors() {
            CreateOrderRequest request = Fixtures.createOrderRequest()
                    .lines(List.of(Fixtures.line("bad sku", 0, "-1")))
                    .shippingAddress(Fixtures.address().countryCode("USA"));

            MvcTestResult result = mvc.post().uri(url("/orders")).headers(authenticated())
                    .header("Idempotency-Key", Fixtures.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(request)).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            Problem problem = fromJson(result, Problem.class);
            assertThat(problem.getErrors()).extracting("field")
                    .contains("lines[0].sku", "lines[0].quantity", "lines[0].unitPrice.amount", "shippingAddress.countryCode");
        }

        @Test
        void rejectsTooShortIdempotencyKey() {
            MvcTestResult result = mvc.post().uri(url("/orders")).headers(authenticated())
                    .header("Idempotency-Key", "short")
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.createOrderRequest())).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fromJson(result, Problem.class).getErrors()).extracting("field").contains("Idempotency-Key");
        }

        @Test
        void rejectsMissingIdempotencyKey() {
            MvcTestResult result = mvc.post().uri(url("/orders")).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.createOrderRequest())).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fromJson(result, Problem.class).getDetail()).contains("Idempotency-Key");
        }

        @Test
        void rejectsMalformedJson() {
            MvcTestResult result = mvc.post().uri(url("/orders")).headers(authenticated())
                    .header("Idempotency-Key", Fixtures.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\": ").exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        }
    }

    @Nested
    @DisplayName("GET /orders/{orderId}")
    class GetOrder {

        @Test
        void returnsOrderWithETag() {
            Order created = createOrder();

            MvcTestResult result = mvc.get().uri(url("/orders/{id}"), created.getId()).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatusOk().headers().hasValue(HttpHeaders.ETAG, "\"1\"");
            assertThat(fromJson(result, Order.class)).isEqualTo(created);
        }

        @Test
        void returns404ProblemForUnknownId() {
            MvcTestResult result = mvc.get().uri(url("/orders/{id}"), randomId()).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            Problem problem = fromJson(result, Problem.class);
            assertThat(problem.getType()).hasToString(ProblemFactory.TYPE_NAMESPACE + "not-found");
            assertThat(problem.getInstance().toString()).endsWith(result.getRequest().getRequestURI()).startsWith("http://");
        }

        @Test
        void returns400ForNonUuidPath() {
            MvcTestResult result = mvc.get().uri(url("/orders/not-a-uuid")).headers(authenticated()).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fromJson(result, Problem.class).getDetail()).contains("orderId");
        }
    }

    @Nested
    @DisplayName("GET /orders")
    class ListOrders {

        @Test
        void filtersByStatusCustomerAndTagsWithPaging() {
            Order mine = createOrder();
            Order submitted = createOrder();
            orderService.submit(submitted.getId());

            MvcTestResult result = mvc.get().uri(url("/orders"))
                    .headers(authenticated())
                    .queryParam("status", "PENDING")
                    .queryParam("customerId", Fixtures.CUSTOMER_ID.toString())
                    .queryParam("tag", "gift", "priority")
                    .queryParam("createdAfter", "2020-01-01T00:00:00Z")
                    .queryParam("page", "0")
                    .queryParam("size", "100")
                    .exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatusOk();
            OrderPage page = fromJson(result, OrderPage.class);
            assertThat(page.getSize()).isEqualTo(100);
            assertThat(page.getItems()).extracting(Order::getId).contains(mine.getId()).doesNotContain(submitted.getId());
            assertThat(page.getItems()).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING));
            assertThat(page.getTotalElements()).isEqualTo(page.getItems().size());
        }

        @Test
        void appliesDefaultsWhenNoQueryGiven() {
            createOrder();

            MvcTestResult result = mvc.get().uri(url("/orders")).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            OrderPage page = fromJson(result, OrderPage.class);
            assertThat(page.getPage()).isZero();
            assertThat(page.getSize()).isEqualTo(20);
            assertThat(page.getItems()).hasSizeLessThanOrEqualTo(20);
        }

        @Test
        void rejectsOutOfRangePageSize() {
            MvcTestResult result = mvc.get().uri(url("/orders")).headers(authenticated()).queryParam("size", "500").exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fromJson(result, Problem.class).getErrors()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.getField()).isEqualTo("size");
                        assertThat(e.getRejectedValue()).isEqualTo("500");
                    });
        }

        @Test
        void rejectsUnknownStatusEnum() {
            MvcTestResult result = mvc.get().uri(url("/orders")).headers(authenticated()).queryParam("status", "LOST").exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("PUT /orders/{orderId}")
    class ReplaceOrder {

        @Test
        void replacesPendingOrderAndBumpsVersion() {
            Order created = createOrder();
            UpdateOrderRequest update = Fixtures.updateOrderRequest();

            MvcTestResult result = mvc.put().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .header(HttpHeaders.IF_MATCH, "\"1\"")
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(update)).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatusOk().headers().hasValue(HttpHeaders.ETAG, "\"2\"");
            Order updated = fromJson(result, Order.class);
            assertThat(updated.getVersion()).isEqualTo(2L);
            assertThat(updated.getLines()).containsExactlyElementsOf(update.getLines());
            assertThat(updated.getShippingAddress()).isEqualTo(update.getShippingAddress());
            assertThat(updated.getTotal().getAmount()).isEqualByComparingTo("149.00");
            assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        }

        @Test
        void returns412WhenIfMatchIsStale() {
            Order created = createOrder();

            MvcTestResult result = mvc.put().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .header(HttpHeaders.IF_MATCH, "\"7\"")
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.updateOrderRequest())).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
        }

        @Test
        void returns409WhenOrderIsNoLongerPending() {
            Order created = createOrder();
            orderService.submit(created.getId());

            MvcTestResult result = mvc.put().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.updateOrderRequest())).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(fromJson(result, Problem.class).getDetail()).contains("SUBMITTED");
        }

        @Test
        void returns404ForUnknownOrder() {
            MvcTestResult result = mvc.put().uri(url("/orders/{id}"), randomId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.updateOrderRequest())).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns422ForUnknownSkuOnReplace() {
            Order created = createOrder();

            MvcTestResult result = mvc.put().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Fixtures.updateOrderRequest().lines(List.of(Fixtures.line("NOPE-9", 1, "1.00"))))).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        }

        @Test
        void returns400ForMalformedIfMatch() {
            Order created = createOrder();

            MvcTestResult result = mvc.put().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .header(HttpHeaders.IF_MATCH, "\"latest\"")
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(Fixtures.updateOrderRequest())).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fromJson(result, Problem.class).getDetail()).contains("If-Match").contains("latest");
        }

        @Test
        void rejectsEmptyLines() {
            Order created = createOrder();

            MvcTestResult result = mvc.put().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Fixtures.updateOrderRequest().lines(List.of()))).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fromJson(result, Problem.class).getErrors()).extracting("field").contains("lines");
        }
    }

    @Nested
    @DisplayName("PATCH /orders/{orderId}")
    class PatchOrder {

        @Test
        void updatesOnlyProvidedFields() {
            Order created = createOrder();

            MvcTestResult result = mvc.patch().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new OrderPatch().notes("Ring twice"))).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatusOk();
            Order patched = fromJson(result, Order.class);
            assertThat(patched.getNotes()).isEqualTo("Ring twice");
            assertThat(patched.getTags()).isEqualTo(created.getTags());
            assertThat(patched.getShippingAddress()).isEqualTo(created.getShippingAddress());
            assertThat(patched.getLines()).isEqualTo(created.getLines());
            assertThat(patched.getVersion()).isEqualTo(2L);
        }

        @Test
        void distinguishesAbsentFromNullNotes() {
            Order created = createOrder();
            assertThat(created.getNotes()).isNotNull();

            MvcTestResult untouched = mvc.patch().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"tags\":[\"only-tags\"]}").exchange();
            assertExchangeConforms(untouched);
            assertThat(fromJson(untouched, Order.class).getNotes()).isEqualTo(created.getNotes());
            assertThat(fromJson(untouched, Order.class).getTags()).containsExactly("only-tags");

            MvcTestResult cleared = mvc.patch().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":null,\"tags\":[]}").exchange();
            assertExchangeConforms(cleared);
            Order afterClear = fromJson(cleared, Order.class);
            assertThat(afterClear.getNotes()).isNull();
            assertThat(afterClear.getTags()).isEmpty();
            assertThat(afterClear.getShippingAddress()).isEqualTo(created.getShippingAddress());
        }

        @Test
        void returns404ForUnknownOrder() {
            MvcTestResult result = mvc.patch().uri(url("/orders/{id}"), randomId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(new OrderPatch().notes("x"))).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns409WhenOrderIsCancelled() {
            Order created = createOrder();
            orderService.cancel(created.getId(), null);

            MvcTestResult result = mvc.patch().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON).content(toJson(new OrderPatch().notes("x"))).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.CONFLICT);
        }

        @Test
        void rejectsOverlongNotes() {
            Order created = createOrder();

            MvcTestResult result = mvc.patch().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new OrderPatch().notes("x".repeat(501)))).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fromJson(result, Problem.class).getErrors()).extracting("field").containsExactly("notes");
        }
    }

    @Nested
    @DisplayName("DELETE /orders/{orderId}")
    class CancelOrder {

        @Test
        void cancelsWithNoContentRecordsTheReasonAndIsIdempotent() {
            Order created = createOrder();

            MvcTestResult first = mvc.delete().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .queryParam("reason", "changed my mind").exchange();
            MvcTestResult second = mvc.delete().uri(url("/orders/{id}"), created.getId()).headers(authenticated())
                    .queryParam("reason", "a later reason that must not overwrite the first").exchange();

            assertExchangeConforms(first);
            assertExchangeConforms(second);
            assertThat(first).hasStatus(HttpStatus.NO_CONTENT).body().isEmpty();
            assertThat(second).hasStatus(HttpStatus.NO_CONTENT);
            MvcTestResult after = mvc.get().uri(url("/orders/{id}"), created.getId()).headers(authenticated()).exchange();
            assertExchangeConforms(after);
            Order cancelled = fromJson(after, Order.class);
            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(cancelled.getCancellationReason()).isEqualTo("changed my mind");
            assertThat(cancelled.getVersion()).isEqualTo(2L);
        }

        @Test
        void returns400ForNonUuidPath() {
            MvcTestResult result = mvc.delete().uri(url("/orders/not-a-uuid")).headers(authenticated()).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns409WhenAlreadyShipped() {
            Order created = createOrder();
            orderService.forceState(created.getId(), OrderState.SHIPPED);

            MvcTestResult result = mvc.delete().uri(url("/orders/{id}"), created.getId()).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        }

        @Test
        void returns404ForUnknownOrder() {
            MvcTestResult result = mvc.delete().uri(url("/orders/{id}"), randomId()).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /orders/{orderId}/submit")
    class SubmitOrder {

        @Test
        void transitionsPendingToSubmitted() {
            Order created = createOrder();

            MvcTestResult result = mvc.post().uri(url("/orders/{id}/submit"), created.getId()).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatusOk();
            assertThat(fromJson(result, Order.class).getStatus()).isEqualTo(OrderStatus.SUBMITTED);
        }

        @Test
        void returns404ForUnknownOrder() {
            MvcTestResult result = mvc.post().uri(url("/orders/{id}/submit"), randomId()).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void returns400ForNonUuidPath() {
            MvcTestResult result = mvc.post().uri(url("/orders/not-a-uuid/submit")).headers(authenticated()).exchange();

            assertResponseConforms(result);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void returns409WhenSubmittedTwice() {
            Order created = createOrder();
            mvc.post().uri(url("/orders/{id}/submit"), created.getId()).headers(authenticated()).exchange();

            MvcTestResult result = mvc.post().uri(url("/orders/{id}/submit"), created.getId()).headers(authenticated()).exchange();

            assertExchangeConforms(result);
            assertThat(result).hasStatus(HttpStatus.CONFLICT);
        }
    }
}
