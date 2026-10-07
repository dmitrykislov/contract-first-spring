package io.github.dmitrykislov.orders.server.web;

import static io.github.dmitrykislov.orders.testsupport.MockMvcContract.assertExchangeConforms;
import static io.github.dmitrykislov.orders.testsupport.MockMvcContract.assertResponseConforms;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.orders.server.model.Problem;
import io.github.dmitrykislov.orders.server.model.Product;
import io.github.dmitrykislov.orders.server.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class CatalogApiConformanceTest extends ApiTestBase {

    @Test
    void returnsProductInDefaultLanguage() {
        MvcTestResult result = mvc.get().uri(url("/catalog/products/{sku}"), "WIDGET-BLUE-L").headers(authenticated()).exchange();

        assertExchangeConforms(result);
        assertThat(result).hasStatusOk();
        Product product = fromJson(result, Product.class);
        assertThat(product.getName()).isEqualTo("Large blue widget");
        assertThat(product.getInStock()).isTrue();
    }

    @Test
    void honoursAcceptLanguageHeader() {
        MvcTestResult result = mvc.get().uri(url("/catalog/products/{sku}"), "WIDGET-BLUE-L").headers(authenticated())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9").exchange();

        assertExchangeConforms(result);
        assertThat(fromJson(result, Product.class).getName()).isEqualTo("Großes blaues Widget");
    }

    @Test
    void fallsBackToEnglishForUnsupportedOrMalformedLanguage() {
        MvcTestResult french = mvc.get().uri(url("/catalog/products/{sku}"), "WIDGET-BLUE-L").headers(authenticated())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,fr;q=0.8").exchange();
        MvcTestResult garbage = mvc.get().uri(url("/catalog/products/{sku}"), "WIDGET-BLUE-L").headers(authenticated())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "***").exchange();

        assertExchangeConforms(french);
        assertThat(fromJson(french, Product.class).getName()).isEqualTo("Large blue widget");
        assertThat(garbage).hasStatusOk();
        assertThat(fromJson(garbage, Product.class).getName()).isEqualTo("Large blue widget");
    }

    @Test
    void returns404ForUnknownSku() {
        MvcTestResult result = mvc.get().uri(url("/catalog/products/{sku}"), "NOPE-1").headers(authenticated()).exchange();

        assertExchangeConforms(result);
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(fromJson(result, Problem.class).getDetail()).contains("NOPE-1");
    }

    @Test
    void rejectsSkuViolatingPattern() {
        MvcTestResult result = mvc.get().uri(url("/catalog/products/{sku}"), "lowercase-sku").headers(authenticated()).exchange();

        assertResponseConforms(result);
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(fromJson(result, Problem.class).getErrors()).extracting("field").containsExactly("sku");
    }
}
