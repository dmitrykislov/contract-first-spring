package io.github.dmitrykislov.orders.server.support;


import io.github.dmitrykislov.orders.server.model.Order;
import io.github.dmitrykislov.contractfirst.testing.Contract;
import io.github.dmitrykislov.orders.spec.OrdersContract;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import io.github.dmitrykislov.contractfirst.server.ContractJson;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots the whole server (filters, advice, converters) against MockMvc. The in-memory store is shared
 * across tests in a context, so tests create their own orders rather than assuming an empty store.
 */
@SpringBootTest(properties = "contract-first.server.api-key.keys=" + Fixtures.API_KEY)
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    /** One shared contract instance per JVM: one validator, one coverage record across all test classes. */
    public static final Contract CONTRACT = Contract.fromClasspath(OrdersContract.RESOURCE, OrdersContract.BASE_PATH);

    protected static void assertExchangeConforms(MvcTestResult result) {
        CONTRACT.mockMvc().assertExchangeConforms(result);
    }

    protected static void assertResponseConforms(MvcTestResult result) {
        CONTRACT.mockMvc().assertResponseConforms(result);
    }

    @Autowired
    protected MockMvcTester mvc;

    /** The HTTP-boundary mapper (NON_NULL, JsonNullable), the same one Spring MVC uses for the API. */
    protected JsonMapper json;

    @Autowired
    void contractJson(ContractJson contractJson) {
        this.json = contractJson.mapper();
    }

    protected String url(String path) {
        return OrdersContract.BASE_PATH + path;
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
        assertExchangeConforms(result);
        return fromJson(result, Order.class);
    }

    protected static UUID randomId() {
        return UUID.randomUUID();
    }
}
