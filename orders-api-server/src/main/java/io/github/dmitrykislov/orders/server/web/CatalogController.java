package io.github.dmitrykislov.orders.server.web;

import io.github.dmitrykislov.orders.server.api.CatalogApi;
import io.github.dmitrykislov.orders.server.domain.ProductCatalog;
import io.github.dmitrykislov.orders.server.model.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CatalogController implements CatalogApi {

    private final ProductCatalog catalog;
    private final OrderMapper mapper;

    public CatalogController(ProductCatalog catalog, OrderMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Product> getProduct(String sku, String acceptLanguage) {
        return catalog.find(sku)
                .map(item -> ResponseEntity.ok(mapper.toApi(item, acceptLanguage)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product %s does not exist".formatted(sku)));
    }
}
