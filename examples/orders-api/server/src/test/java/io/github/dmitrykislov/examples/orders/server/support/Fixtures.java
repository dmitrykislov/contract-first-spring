package io.github.dmitrykislov.examples.orders.server.support;

import io.github.dmitrykislov.examples.orders.server.model.Address;
import io.github.dmitrykislov.examples.orders.server.model.CreateOrderRequest;
import io.github.dmitrykislov.examples.orders.server.model.Money;
import io.github.dmitrykislov.examples.orders.server.model.OrderLine;
import io.github.dmitrykislov.examples.orders.server.model.UpdateOrderRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request payloads built from the generated models so test JSON always matches the contract. */
public final class Fixtures {

    public static final String API_KEY = "test-api-key";
    public static final UUID CUSTOMER_ID = UUID.fromString("0b7a6a4e-4a23-4b1e-9b5b-6a1f1c2d3e4f");

    private Fixtures() {}

    public static Address address() {
        return new Address("1 Example Street", "Springfield", "12345", "US").line2("Suite 4");
    }

    public static OrderLine line(String sku, int quantity, String unitPrice) {
        return new OrderLine(sku, quantity, new Money(new BigDecimal(unitPrice), "EUR"));
    }

    public static CreateOrderRequest createOrderRequest() {
        return new CreateOrderRequest(CUSTOMER_ID, List.of(line("WIDGET-BLUE-L", 2, "19.99"), line("WIDGET-RED-S", 1, "9.50")), address())
                .notes("Leave at the door")
                .tags(Set.of("gift", "priority"));
    }

    public static UpdateOrderRequest updateOrderRequest() {
        return new UpdateOrderRequest(List.of(line("GADGET-X1", 1, "149.00")), address().line1("2 Replacement Road"))
                .notes("Replaced")
                .tags(Set.of("replaced"));
    }

    public static String idempotencyKey() {
        return "idem-" + UUID.randomUUID();
    }
}
