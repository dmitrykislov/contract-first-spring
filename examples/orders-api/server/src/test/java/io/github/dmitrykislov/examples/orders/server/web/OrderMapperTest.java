package io.github.dmitrykislov.examples.orders.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.examples.orders.server.domain.Change;
import io.github.dmitrykislov.examples.orders.server.domain.OrderDraft;
import io.github.dmitrykislov.examples.orders.server.domain.StoredOrder;
import io.github.dmitrykislov.examples.orders.server.model.Address;
import io.github.dmitrykislov.examples.orders.server.model.OrderPatch;
import io.github.dmitrykislov.examples.orders.server.support.Fixtures;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The mapper is a pure function, so the merge-patch semantics can be pinned without a Spring context. */
class OrderMapperTest {

    @Test
    void patchDistinguishesAbsentNullAndValueForNotes() {
        assertThat(OrderMapper.notesChange(new OrderPatch())).isInstanceOf(Change.Keep.class);
        assertThat(OrderMapper.notesChange(new OrderPatch().notes(null))).isInstanceOf(Change.Clear.class);
        assertThat(OrderMapper.notesChange(new OrderPatch().notes("x"))).isEqualTo(Change.set("x"));
    }

    @Test
    void patchTreatsAbsentTagsAsKeepAndEmptyTagsAsReplace() {
        assertThat(OrderMapper.tagsChange(new OrderPatch())).isInstanceOf(Change.Keep.class);
        assertThat(OrderMapper.tagsChange(new OrderPatch().tags(Set.of()))).isEqualTo(Change.set(Set.of()));
        assertThat(OrderMapper.addressChange(new OrderPatch())).isInstanceOf(Change.Keep.class);
        assertThat(OrderMapper.addressChange(new OrderPatch().shippingAddress(Fixtures.address())))
                .isEqualTo(Change.set(new StoredOrder.PostalAddress("1 Example Street", "Suite 4", "Springfield", "12345", "US")));
    }

    @Test
    void draftCarriesEveryFieldAndDefaultsMissingTagsToEmpty() {
        OrderDraft draft = OrderMapper.toDraft(Fixtures.createOrderRequest().tags(null));
        assertThat(draft.customerId()).isEqualTo(Fixtures.CUSTOMER_ID);
        assertThat(draft.lines()).hasSize(2);
        assertThat(draft.tags()).isEmpty();
        assertThat(draft.notes()).isEqualTo("Leave at the door");
    }

    @Test
    void addressRoundTripsIncludingTheOptionalSecondLine() {
        Address withLine2 = Fixtures.address();
        assertThat(OrderMapper.toDomain(withLine2).line2()).isEqualTo("Suite 4");
        assertThat(OrderMapper.toDomain(withLine2.line2(null)).line2()).isNull();
    }
}
