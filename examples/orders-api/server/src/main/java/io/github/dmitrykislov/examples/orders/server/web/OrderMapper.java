package io.github.dmitrykislov.examples.orders.server.web;

import io.github.dmitrykislov.examples.orders.server.domain.Change;
import io.github.dmitrykislov.examples.orders.server.domain.OrderDraft;
import io.github.dmitrykislov.examples.orders.server.domain.OrderQuery;
import io.github.dmitrykislov.examples.orders.server.domain.OrderService;
import io.github.dmitrykislov.examples.orders.server.domain.OrderState;
import io.github.dmitrykislov.examples.orders.server.domain.StoredOrder;
import io.github.dmitrykislov.examples.orders.server.domain.StoredOrder.PostalAddress;
import io.github.dmitrykislov.examples.orders.server.model.Address;
import io.github.dmitrykislov.examples.orders.server.model.CreateOrderRequest;
import io.github.dmitrykislov.examples.orders.server.model.Money;
import io.github.dmitrykislov.examples.orders.server.model.Order;
import io.github.dmitrykislov.examples.orders.server.model.OrderLine;
import io.github.dmitrykislov.examples.orders.server.model.OrderPage;
import io.github.dmitrykislov.examples.orders.server.model.OrderPatch;
import io.github.dmitrykislov.examples.orders.server.model.OrderStatus;
import io.github.dmitrykislov.examples.orders.server.model.UpdateOrderRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Translates between the generated API models (transport) and the domain records. Pure functions with
 * no state or collaborators, so this is a utility class rather than a Spring bean: nothing to inject,
 * nothing to mock, and every method is unit-testable in isolation.
 */
public final class OrderMapper {

    private OrderMapper() {}

    public static Order toApi(StoredOrder order) {
        return Order.builder()
                .id(order.id())
                .customerId(order.customerId())
                .status(OrderStatus.fromValue(order.state().name()))
                .lines(order.lines().stream().map(OrderMapper::toApi).toList())
                .shippingAddress(toApi(order.shippingAddress()))
                .notes(order.notes())
                .tags(order.tags())
                .cancellationReason(order.cancellationReason())
                .total(toApi(order.total()))
                .createdAt(toApi(order.createdAt()))
                .updatedAt(toApi(order.updatedAt()))
                .version(order.version())
                .build();
    }

    public static OrderPage toApi(OrderService.Page page) {
        return new OrderPage(page.items().stream().map(OrderMapper::toApi).toList(), page.page(), page.size(), page.totalElements());
    }

    public static OrderQuery toQuery(@Nullable OrderStatus status, @Nullable UUID customerId,
            @Nullable OffsetDateTime createdAfter, @Nullable List<String> tags, int page, int size) {
        return new OrderQuery(
                status == null ? null : OrderState.valueOf(status.getValue()),
                customerId,
                createdAfter == null ? null : createdAfter.toInstant(),
                tags == null ? Set.of() : Set.copyOf(tags),
                page,
                size);
    }

    public static OrderDraft toDraft(CreateOrderRequest request) {
        return new OrderDraft(request.getCustomerId(), toDomain(request.getLines()),
                toDomain(request.getShippingAddress()), request.getNotes(), tags(request.getTags()));
    }

    public static List<StoredOrder.Line> lines(UpdateOrderRequest request) {
        return toDomain(request.getLines());
    }

    public static PostalAddress toDomain(Address a) {
        return new PostalAddress(a.getLine1(), a.getLine2(), a.getCity(), a.getPostalCode(), a.getCountryCode());
    }

    public static Set<String> tags(@Nullable Set<String> tags) {
        return tags == null ? Set.of() : Set.copyOf(tags);
    }

    /** Absent address: keep. Present: replace (the schema does not allow a null address). */
    public static Change<PostalAddress> addressChange(OrderPatch patch) {
        return patch.getShippingAddress() == null ? Change.keep() : Change.set(toDomain(patch.getShippingAddress()));
    }

    /** {@code notes} is nullable in the contract, so absent, null and a value are three different things. */
    public static Change<String> notesChange(OrderPatch patch) {
        JsonNullable<String> notes = patch.getNotes();
        if (notes == null || !notes.isPresent()) {
            return Change.keep();
        }
        return notes.get() == null ? Change.clear() : Change.set(notes.get());
    }

    /** Absent tags: keep. Any array, including an empty one: replace. */
    public static Change<Set<String>> tagsChange(OrderPatch patch) {
        return patch.getTags() == null ? Change.keep() : Change.set(tags(patch.getTags()));
    }

    private static List<StoredOrder.Line> toDomain(List<OrderLine> lines) {
        return lines.stream().map(l -> new StoredOrder.Line(l.getSku(), l.getQuantity(), toDomain(l.getUnitPrice()))).toList();
    }

    private static OrderLine toApi(StoredOrder.Line line) {
        return new OrderLine(line.sku(), line.quantity(), toApi(line.unitPrice()));
    }

    static Money toApi(StoredOrder.Amount amount) {
        return new Money(amount.amount(), amount.currency());
    }

    private static StoredOrder.Amount toDomain(Money money) {
        return new StoredOrder.Amount(money.getAmount(), money.getCurrency());
    }

    private static Address toApi(PostalAddress a) {
        return new Address(a.line1(), a.city(), a.postalCode(), a.countryCode()).line2(a.line2());
    }

    private static OffsetDateTime toApi(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
