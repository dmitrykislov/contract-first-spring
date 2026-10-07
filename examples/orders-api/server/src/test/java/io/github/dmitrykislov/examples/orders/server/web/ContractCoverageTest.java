package io.github.dmitrykislov.examples.orders.server.web;

import io.github.dmitrykislov.contractfirst.testing.Contract;
import io.github.dmitrykislov.contractfirst.testing.ServerRouteCoverageSupport;
import io.github.dmitrykislov.examples.orders.server.support.ApiTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Every operation in the contract is routed by Spring MVC exactly once. */
@SpringBootTest(properties = "contract-first.server.api-key.keys=test")
class ContractCoverageTest extends ServerRouteCoverageSupport {

    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    @Override
    protected Contract contract() {
        return ApiTestBase.CONTRACT;
    }

    @Override
    protected RequestMappingHandlerMapping handlerMapping() {
        return handlerMapping;
    }
}
