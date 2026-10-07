package io.github.dmitrykislov.examples.orders.server.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Fixed in-memory catalog; enough to exercise lookups and SKU validation. */
@Repository
public class ProductCatalog {

    private final Map<String, CatalogItem> items;

    public ProductCatalog() {
        this(List.of(
                new CatalogItem("WIDGET-BLUE-L", Map.of("en", "Large blue widget", "de", "Großes blaues Widget"),
                        new StoredOrder.Amount(new BigDecimal("19.99"), "EUR"), true),
                new CatalogItem("WIDGET-RED-S", Map.of("en", "Small red widget", "de", "Kleines rotes Widget"),
                        new StoredOrder.Amount(new BigDecimal("9.50"), "EUR"), true),
                new CatalogItem("GADGET-X1", Map.of("en", "Gadget X1"),
                        new StoredOrder.Amount(new BigDecimal("149.00"), "EUR"), false)));
    }

    ProductCatalog(List<CatalogItem> items) {
        this.items = items.stream().collect(Collectors.toUnmodifiableMap(CatalogItem::sku, Function.identity()));
    }

    public Optional<CatalogItem> find(String sku) {
        return Optional.ofNullable(items.get(sku));
    }

    public boolean exists(String sku) {
        return items.containsKey(sku);
    }
}
