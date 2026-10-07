package io.github.dmitrykislov.orders.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.IdempotencyKeyReused;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.IllegalOrderState;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.MixedCurrencies;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.OrderNotFound;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.UnknownSku;
import io.github.dmitrykislov.orders.server.domain.OrdersDomainException.VersionMismatch;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private MutableClock clock;
    private OrderService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        service = new OrderService(new ProductCatalog(), clock);
    }

    @Nested
    class Create {

        @Test
        void createsPendingOrderWithComputedTotal() {
            OrderService.CreationResult result = service.create("k-1", draft());

            assertThat(result.replayed()).isFalse();
            StoredOrder order = result.order();
            assertThat(order.state()).isEqualTo(OrderState.PENDING);
            assertThat(order.version()).isEqualTo(1);
            assertThat(order.createdAt()).isEqualTo(T0);
            assertThat(order.total().amount()).isEqualByComparingTo("49.48");
            assertThat(service.get(order.id())).isEqualTo(order);
        }

        @Test
        void replaysIdenticalDraftForTheSameKeyWithoutCreatingAnother() {
            StoredOrder first = service.create("k-dup", draft()).order();
            OrderService.CreationResult replay = service.create("k-dup", draft());

            assertThat(replay.replayed()).isTrue();
            assertThat(replay.order()).isEqualTo(first);
            assertThat(service.list(new OrderQuery(null, first.customerId(), null, Set.of(), 0, 10)).totalElements()).isEqualTo(1);
        }

        @Test
        void rejectsSameKeyWithDifferentDraft() {
            service.create("k-dup2", draft());
            OrderDraft different = new OrderDraft(draft().customerId(), draft().lines(), draft().shippingAddress(), "other notes", Set.of());

            assertThatThrownBy(() -> service.create("k-dup2", different)).isInstanceOf(IdempotencyKeyReused.class);
        }

        @Test
        void rejectsUnknownSkuAndMixedCurrenciesWithoutClaimingTheKey() {
            OrderDraft unknown = draft(List.of(line("NOPE", 1, "1.00", "EUR")));
            assertThatThrownBy(() -> service.create("k-bad", unknown)).isInstanceOf(UnknownSku.class);

            OrderDraft mixed = draft(List.of(line("WIDGET-BLUE-L", 1, "19.99", "EUR"), line("WIDGET-RED-S", 1, "9.50", "USD")));
            assertThatThrownBy(() -> service.create("k-bad", mixed)).isInstanceOf(MixedCurrencies.class);

            // the key was never claimed, so a valid retry with it succeeds
            assertThat(service.create("k-bad", draft()).replayed()).isFalse();
        }
    }

    @Nested
    class Modify {

        @Test
        void replaceChecksVersionAndState() {
            StoredOrder order = service.create("k-3", draft()).order();
            clock.advance(Duration.ofMinutes(1));

            StoredOrder replaced = service.replace(order.id(), OptionalLong.of(1), List.of(line("GADGET-X1", 1, "149.00", "EUR")),
                    address(), "n", Set.of("t"));
            assertThat(replaced.version()).isEqualTo(2);
            assertThat(replaced.updatedAt()).isEqualTo(T0.plus(Duration.ofMinutes(1)));
            assertThat(replaced.createdAt()).isEqualTo(T0);

            assertThatThrownBy(() -> service.replace(order.id(), OptionalLong.of(1), order.lines(), address(), null, Set.of()))
                    .isInstanceOf(VersionMismatch.class);

            service.submit(order.id());
            assertThatThrownBy(() -> service.replace(order.id(), OptionalLong.empty(), order.lines(), address(), null, Set.of()))
                    .isInstanceOf(IllegalOrderState.class);
        }

        @Test
        void patchAppliesKeepSetAndClearIndependently() {
            StoredOrder order = service.create("k-patch", draft()).order();

            StoredOrder patched = service.patch(order.id(), Change.keep(), Change.clear(), Change.set(Set.of("x")));

            assertThat(patched.shippingAddress()).isEqualTo(order.shippingAddress());
            assertThat(patched.notes()).isNull();
            assertThat(patched.tags()).containsExactly("x");
            assertThat(patched.version()).isEqualTo(2);

            StoredOrder untouched = service.patch(order.id(), Change.keep(), Change.keep(), Change.keep());
            assertThat(untouched.notes()).isNull();
            assertThat(untouched.tags()).containsExactly("x");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void transitionsAndCancellationReason() {
            StoredOrder order = service.create("k-4", draft()).order();

            assertThat(service.submit(order.id()).state()).isEqualTo(OrderState.SUBMITTED);
            assertThatThrownBy(() -> service.submit(order.id())).isInstanceOf(IllegalOrderState.class);

            service.cancel(order.id(), "first reason");
            service.cancel(order.id(), "second reason"); // idempotent, keeps the first
            StoredOrder cancelled = service.get(order.id());
            assertThat(cancelled.state()).isEqualTo(OrderState.CANCELLED);
            assertThat(cancelled.cancellationReason()).isEqualTo("first reason");

            StoredOrder shipped = service.create("k-5", draft()).order();
            service.forceState(shipped.id(), OrderState.SHIPPED);
            assertThatThrownBy(() -> service.cancel(shipped.id(), null)).isInstanceOf(IllegalOrderState.class);
            assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(OrderNotFound.class);
        }
    }

    @Test
    void listFiltersAndPages() {
        UUID customer = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofSeconds(1));
            service.create("k-list-" + i, new OrderDraft(customer, List.of(line("WIDGET-RED-S", 1, "9.50", "EUR")),
                    address(), null, Set.of("even-" + (i % 2))));
        }

        OrderService.Page firstPage = service.list(new OrderQuery(null, customer, null, Set.of(), 0, 2));
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.items()).hasSize(2);

        OrderService.Page lastPage = service.list(new OrderQuery(null, customer, null, Set.of(), 2, 2));
        assertThat(lastPage.items()).hasSize(1);

        OrderService.Page beyond = service.list(new OrderQuery(null, customer, null, Set.of(), 50, 2));
        assertThat(beyond.items()).isEmpty();

        // even-0 orders were created at +1s, +3s, +5s; only the last two are after +2s
        OrderService.Page tagged = service.list(new OrderQuery(OrderState.PENDING, customer, T0.plusSeconds(2), Set.of("even-0"), 0, 10));
        assertThat(tagged.items()).hasSize(2)
                .allSatisfy(o -> assertThat(o.createdAt()).isAfter(T0.plusSeconds(2)));
    }

    private static OrderDraft draft() {
        return draft(List.of(line("WIDGET-BLUE-L", 2, "19.99", "EUR"), line("WIDGET-RED-S", 1, "9.50", "EUR")));
    }

    private static OrderDraft draft(List<StoredOrder.Line> lines) {
        return new OrderDraft(UUID.fromString("0b7a6a4e-4a23-4b1e-9b5b-6a1f1c2d3e4f"), lines, address(), "notes", Set.of("gift"));
    }

    private static StoredOrder.Line line(String sku, int qty, String price, String currency) {
        return new StoredOrder.Line(sku, qty, new StoredOrder.Amount(new BigDecimal(price), currency));
    }

    private static StoredOrder.PostalAddress address() {
        return new StoredOrder.PostalAddress("1 Street", null, "City", "0000", "US");
    }

    /** Minimal controllable clock; avoids pulling in a mocking framework for one collaborator. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
