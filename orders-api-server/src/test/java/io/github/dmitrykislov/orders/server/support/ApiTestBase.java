package io.github.dmitrykislov.orders.server.support;

import static io.github.dmitrykislov.orders.testsupport.OrdersContract.BASE_PATH;

import io.github.dmitrykislov.orders.server.model.Order;
import io.github.dmitrykislov.orders.testsupport.MockMvcContract;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots the whole server (filters, advice, converters) against MockMvc. The in-memory store is shared
 * across tests in a context, so tests create their own orders rather than assuming an empty store.
 */
@SpringBootTest(properties = "orders.server.security.api-keys=" + Fixtures.API_KEY)
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JsonMapper json;

    protected String url(String path) {
        return BASE_PATH + path;
    }

    protected String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    protected <T> T fromJson(MvcTestResult result, Class<T> type) {
        return json.readValue(result.getResponse().getContentAsByteArray(), type);
    }

    protected HttpHeaders authenticated() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", Fixtures.API_KEY);
        return headers;
    }

    /** Creates an order through the API and returns it, asserting the exchange itself was conformant. */
    protected Order createOrder() {
        MvcTestResult result = mvc.post().uri(url("/orders"))
                .headers(authenticated())
                .header("Idempotency-Key", Fixtures.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Fixtures.createOrderRequest()))
                .exchange();
        MockMvcContract.assertExchangeConforms(result);
        return fromJson(result, Order.class);
    }

    protected static UUID randomId() {
        return UUID.randomUUID();
    }
}
