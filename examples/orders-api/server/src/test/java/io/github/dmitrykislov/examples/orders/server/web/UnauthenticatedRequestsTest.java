package io.github.dmitrykislov.examples.orders.server.web;

import io.github.dmitrykislov.contractfirst.testing.Contract;
import io.github.dmitrykislov.contractfirst.testing.UnauthenticatedRequestsSupport;
import io.github.dmitrykislov.examples.orders.server.support.ApiTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The contract requires the API key on every operation: prove the 401 problem on every route. */
@SpringBootTest(properties = "contract-first.server.api-key.keys=test")
@AutoConfigureMockMvc
class UnauthenticatedRequestsTest extends UnauthenticatedRequestsSupport {

    @Autowired
    MockMvcTester mvc;

    @Override
    protected Contract contract() {
        return ApiTestBase.CONTRACT;
    }

    @Override
    protected MockMvcTester mvc() {
        return mvc;
    }
}
