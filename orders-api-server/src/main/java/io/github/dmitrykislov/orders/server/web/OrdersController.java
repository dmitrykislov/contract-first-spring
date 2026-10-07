package io.github.dmitrykislov.orders.server.web;

import io.github.dmitrykislov.orders.server.api.OrdersApi;
import io.github.dmitrykislov.orders.server.domain.OrderService;
import io.github.dmitrykislov.orders.server.domain.StoredOrder;
import io.github.dmitrykislov.orders.server.model.CreateOrderRequest;
import io.github.dmitrykislov.orders.server.model.Order;
import io.github.dmitrykislov.orders.server.model.OrderPage;
import io.github.dmitrykislov.orders.server.model.OrderPatch;
import io.github.dmitrykislov.orders.server.model.OrderStatus;
import io.github.dmitrykislov.orders.server.model.UpdateOrderRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Implements the generated {@link OrdersApi}. The interface carries every mapping, consumes/produces
 * and validation annotation from the contract; this class only adapts to the domain service. Because
 * the interface is generated without default methods, forgetting an operation is a compile error.
 */
@RestController
public class OrdersController implements OrdersApi {

    private final OrderService orders;
    private final OrderMapper mapper;

    public OrdersController(OrderService orders, OrderMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<OrderPage> listOrders(@Nullable UUID xRequestId, @Nullable OrderStatus status,
            @Nullable UUID customerId, @Nullable OffsetDateTime createdAfter, @Nullable List<String> tag,
            Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toApi(orders.list(mapper.toQuery(status, customerId, createdAfter, tag, page, size))));
    }

    /** A replay with the same key and payload returns the original 201, as the contract promises. */
    @Override
    public ResponseEntity<Order> createOrder(String idempotencyKey, CreateOrderRequest request, @Nullable UUID xRequestId) {
        OrderService.CreationResult result = orders.create(idempotencyKey, mapper.toDraft(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(result.order().id());
        return ResponseEntity.created(location).body(mapper.toApi(result.order()));
    }

    @Override
    public ResponseEntity<Order> getOrder(UUID orderId, @Nullable UUID xRequestId) {
        StoredOrder order = orders.get(orderId);
        return ResponseEntity.ok().eTag(etag(order)).body(mapper.toApi(order));
    }

    @Override
    public ResponseEntity<Order> replaceOrder(UUID orderId, UpdateOrderRequest request, @Nullable UUID xRequestId,
            @Nullable String ifMatch) {
        StoredOrder updated = orders.replace(
                orderId,
                parseIfMatch(ifMatch),
                mapper.lines(request),
                mapper.toDomain(request.getShippingAddress()),
                request.getNotes(),
                mapper.tags(request.getTags()));
        return ResponseEntity.ok().eTag(etag(updated)).body(mapper.toApi(updated));
    }

    @Override
    public ResponseEntity<Order> patchOrder(UUID orderId, OrderPatch patch, @Nullable UUID xRequestId) {
        StoredOrder updated = orders.patch(orderId,
                mapper.addressChange(patch), mapper.notesChange(patch), mapper.tagsChange(patch));
        return ResponseEntity.ok().eTag(etag(updated)).body(mapper.toApi(updated));
    }

    @Override
    public ResponseEntity<Void> cancelOrder(UUID orderId, @Nullable UUID xRequestId, @Nullable String reason) {
        orders.cancel(orderId, reason);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Order> submitOrder(UUID orderId, @Nullable UUID xRequestId) {
        StoredOrder submitted = orders.submit(orderId);
        return ResponseEntity.ok().eTag(etag(submitted)).body(mapper.toApi(submitted));
    }

    static String etag(StoredOrder order) {
        return Long.toString(order.version());
    }

    /**
     * Accepts the bare version or the quoted (optionally weak) form produced by {@code ETag}. Anything
     * else is a malformed request, not a failed precondition.
     */
    private static OptionalLong parseIfMatch(@Nullable String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return OptionalLong.empty();
        }
        String stripped = ifMatch.trim().replaceFirst("^W/", "").replaceAll("^\"|\"$", "");
        try {
            return OptionalLong.of(Long.parseLong(stripped));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "If-Match must be an order version such as \"3\", got '%s'".formatted(ifMatch));
        }
    }
}
