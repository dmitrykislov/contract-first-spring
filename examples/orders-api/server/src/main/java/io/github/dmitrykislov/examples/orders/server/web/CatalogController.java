package io.github.dmitrykislov.examples.orders.server.web;

import io.github.dmitrykislov.examples.orders.server.api.CatalogApi;
import io.github.dmitrykislov.examples.orders.server.domain.ProductCatalog;
import io.github.dmitrykislov.examples.orders.server.model.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CatalogController implements CatalogApi {

    private final ProductCatalog catalog;

    public CatalogController(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public ResponseEntity<Product> getProduct(String sku, String acceptLanguage) {
        return catalog.find(sku)
                .map(item -> ResponseEntity.ok(ProductMapper.toApi(item, acceptLanguage)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product %s does not exist".formatted(sku)));
    }
}
