package io.github.dmitrykislov.orders.server.domain;

import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.IdempotencyKeyReused;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.IllegalOrderState;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.MixedCurrencies;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.OrderNotFound;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.UnknownSku;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.VersionMismatch;
import io.github.dmitrykislov.orders.server.domain.StoredOrder.Line;
import io.github.dmitrykislov.orders.server.domain.StoredOrder.PostalAddress;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * In-memory order management. Thread-safe through per-key atomic updates on
 * {@link ConcurrentHashMap}s; good enough for a reference implementation and for tests.
 */
@Service
public class OrderService {

    private final Map<UUID, StoredOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, Creation> creations = new ConcurrentHashMap<>();
    private final ProductCatalog catalog;
    private final Clock clock;

    public OrderService(ProductCatalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    public record Page(List<StoredOrder> items, int page, int size, long totalElements) {}

    /** Outcome of {@link #create}: the order, and whether this call merely replayed an earlier one. */
    public record CreationResult(StoredOrder order, boolean replayed) {}

    /** What an idempotency key was first used for. */
    private record Creation(OrderDraft draft, UUID orderId) {}

    public Page list(OrderQuery query) {
        List<StoredOrder> matching = orders.values().stream()
                .filter(query::matches)
                .sorted(Comparator.comparing(StoredOrder::createdAt).thenComparing(StoredOrder::id))
                .toList();
        int from = (int) Math.min((long) query.page() * query.size(), matching.size());
        int to = Math.min(from + query.size(), matching.size());
        return new Page(matching.subList(from, to), query.page(), query.size(), matching.size());
    }

    public StoredOrder get(UUID orderId) {
        StoredOrder order = orders.get(orderId);
        if (order == null) {
            throw new OrderNotFound(orderId);
        }
        return order;
    }

    /**
     * Creates an order, or replays the earlier creation made with the same idempotency key and an
     * equal draft. The key is claimed and the order stored in one atomic step, so a key can never
     * point at an order that does not exist.
     */
    public CreationResult create(String idempotencyKey, OrderDraft draft) {
        validateLines(draft.lines());
        UUID newId = UUID.randomUUID();
        Creation creation = creations.computeIfAbsent(idempotencyKey, key -> {
            orders.put(newId, StoredOrder.create(newId, draft, clock.instant()));
            return new Creation(draft, newId);
        });
        boolean replayed = !creation.orderId().equals(newId);
        if (replayed && !creation.draft().equals(draft)) {
            throw new IdempotencyKeyReused(idempotencyKey);
        }
        return new CreationResult(get(creation.orderId()), replayed);
    }

    public StoredOrder replace(UUID orderId, OptionalLong expectedVersion, List<Line> lines,
            PostalAddress address, @Nullable String notes, Set<String> tags) {
        validateLines(lines);
        return update(orderId, current -> {
            expectedVersion.ifPresent(v -> {
                if (v != current.version()) {
                    throw new VersionMismatch(orderId, v, current.version());
                }
            });
            requireMutable(current, "modified");
            return current.withContent(lines, address, notes, tags, clock.instant());
        });
    }

    public StoredOrder patch(UUID orderId, Change<PostalAddress> address, Change<String> notes, Change<Set<String>> tags) {
        return update(orderId, current -> {
            requireMutable(current, "modified");
            Set<String> newTags = tags.applyTo(current.tags());
            return current.withContent(
                    current.lines(),
                    address.applyTo(current.shippingAddress()),
                    notes.applyTo(current.notes()),
                    newTags == null ? Set.of() : newTags,
                    clock.instant());
        });
    }

    public StoredOrder submit(UUID orderId) {
        return update(orderId, current -> {
            if (current.state() != OrderState.PENDING) {
                throw new IllegalOrderState(orderId, current.state(), "submitted");
            }
            return current.transitioned(OrderState.SUBMITTED, clock.instant());
        });
    }

    public void cancel(UUID orderId, @Nullable String reason) {
        update(orderId, current -> {
            if (current.state() == OrderState.CANCELLED) {
                return current; // idempotent no-op; the first reason is kept
            }
            if (!current.state().isCancellable()) {
                throw new IllegalOrderState(orderId, current.state(), "cancelled");
            }
            return current.cancelled(reason, clock.instant());
        });
    }

    /** Test hook: force a state the public API cannot reach (e.g. SHIPPED). */
    public StoredOrder forceState(UUID orderId, OrderState state) {
        return update(orderId, current -> current.transitioned(state, clock.instant()));
    }

    private StoredOrder update(UUID orderId, UnaryOperator<StoredOrder> mutation) {
        StoredOrder updated = orders.computeIfPresent(orderId, (id, current) -> mutation.apply(current));
        if (updated == null) {
            throw new OrderNotFound(orderId);
        }
        return updated;
    }

    private static void requireMutable(StoredOrder order, String attempted) {
        if (!order.state().isMutable()) {
            throw new IllegalOrderState(order.id(), order.state(), attempted);
        }
    }

    private void validateLines(List<Line> lines) {
        lines.stream().map(Line::sku).filter(sku -> !catalog.exists(sku)).findFirst()
                .ifPresent(sku -> { throw new UnknownSku(sku); });
        Set<String> currencies = lines.stream().map(l -> l.unitPrice().currency()).collect(Collectors.toSet());
        if (currencies.size() > 1) {
            throw new MixedCurrencies(currencies);
        }
    }
}
