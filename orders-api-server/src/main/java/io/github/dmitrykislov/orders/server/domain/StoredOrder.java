package io.github.dmitrykislov.orders.server.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of an order as held by the service. Every mutation produces a new instance
 * with an incremented {@link #version()}.
 */
public record StoredOrder(
        UUID id,
        UUID customerId,
        OrderState state,
        List<Line> lines,
        PostalAddress shippingAddress,
        @Nullable String notes,
        Set<String> tags,
        @Nullable String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public StoredOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(shippingAddress, "shippingAddress");
        lines = List.copyOf(lines);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
    }

    static StoredOrder create(UUID id, OrderDraft draft, Instant now) {
        return new StoredOrder(id, draft.customerId(), OrderState.PENDING, draft.lines(), draft.shippingAddress(),
                draft.notes(), draft.tags(), null, now, now, 1);
    }

    /** Sum of line totals. Lines are guaranteed to share one currency by {@link OrderService}. */
    public Amount total() {
        String currency = lines.getFirst().unitPrice().currency();
        BigDecimal sum = lines.stream()
                .map(l -> l.unitPrice().amount().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Amount(sum, currency);
    }

    public StoredOrder transitioned(OrderState newState, Instant now) {
        return new StoredOrder(id, customerId, newState, lines, shippingAddress, notes, tags, cancellationReason,
                createdAt, now, version + 1);
    }

    public StoredOrder cancelled(@Nullable String reason, Instant now) {
        return new StoredOrder(id, customerId, OrderState.CANCELLED, lines, shippingAddress, notes, tags, reason,
                createdAt, now, version + 1);
    }

    public StoredOrder withContent(List<Line> newLines, PostalAddress newAddress, @Nullable String newNotes,
            Set<String> newTags, Instant now) {
        return new StoredOrder(id, customerId, state, newLines, newAddress, newNotes, newTags, cancellationReason,
                createdAt, now, version + 1);
    }

    public record Line(String sku, int quantity, Amount unitPrice) {}

    public record Amount(BigDecimal amount, String currency) {}

    public record PostalAddress(String line1, @Nullable String line2, String city, String postalCode, String countryCode) {}
}
