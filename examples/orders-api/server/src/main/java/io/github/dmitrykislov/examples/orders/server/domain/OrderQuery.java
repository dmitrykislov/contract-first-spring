package io.github.dmitrykislov.examples.orders.server.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Filter and paging criteria for {@link OrderService#list(OrderQuery)}. */
public record OrderQuery(
        @Nullable OrderState state,
        @Nullable UUID customerId,
        @Nullable Instant createdAfter,
        Set<String> tags,
        int page,
        int size) {

    public OrderQuery {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    boolean matches(StoredOrder order) {
        return (state == null || order.state() == state)
                && (customerId == null || order.customerId().equals(customerId))
                && (createdAfter == null || order.createdAt().isAfter(createdAfter))
                && order.tags().containsAll(tags);
    }
}
