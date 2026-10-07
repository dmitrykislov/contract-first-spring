package io.github.dmitrykislov.examples.orders.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Boots with the shipped {@code application.yaml} and no property overrides, exactly like
 * {@code java -jar}. The other tests pass keys as a single string; this one proves the YAML list form
 * switches security on too.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationYamlStartupTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void demoKeysFromApplicationYamlAreEnforced() {
        assertThat(mvc.get().uri("/api/v1/catalog/products/WIDGET-BLUE-L").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/catalog/products/WIDGET-BLUE-L").header("X-API-Key", "dev-api-key-1").exchange())
                .hasStatusOk();
    }
}
