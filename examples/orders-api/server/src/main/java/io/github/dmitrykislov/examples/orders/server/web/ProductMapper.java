package io.github.dmitrykislov.examples.orders.server.web;

import io.github.dmitrykislov.examples.orders.server.domain.CatalogItem;
import io.github.dmitrykislov.examples.orders.server.model.Product;
import org.jspecify.annotations.Nullable;

/** Catalog item to the generated {@link Product}; a pure function, hence not a bean. */
public final class ProductMapper {

    private ProductMapper() {}

    public static Product toApi(CatalogItem item, @Nullable String acceptLanguage) {
        return new Product(item.sku(), item.nameFor(acceptLanguage), OrderMapper.toApi(item.price()), item.inStock());
    }
}
