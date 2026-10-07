package io.github.dmitrykislov.examples.orders.server.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Everything a client supplies to create an order. Being a value object, two drafts are equal when
 * their content is equal, which is exactly the comparison idempotent replay needs.
 */
public record OrderDraft(
        UUID customerId,
        List<StoredOrder.Line> lines,
        StoredOrder.PostalAddress shippingAddress,
        @Nullable String notes,
        Set<String> tags) {

    public OrderDraft {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(shippingAddress, "shippingAddress");
        lines = List.copyOf(lines);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
