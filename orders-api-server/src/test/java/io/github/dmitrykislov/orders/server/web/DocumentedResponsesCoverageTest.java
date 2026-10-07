package io.github.dmitrykislov.orders.server.web;

import io.github.dmitrykislov.contractfirst.testing.Contract;
import io.github.dmitrykislov.contractfirst.testing.DocumentedResponsesCoverageSupport;
import io.github.dmitrykislov.orders.server.support.ApiTestBase;
import org.junit.jupiter.api.Order;

/** Runs last (see junit-platform.properties): every documented response must have been produced by some test. */
@Order(Integer.MAX_VALUE)
class DocumentedResponsesCoverageTest extends DocumentedResponsesCoverageSupport {

    @Override
    protected Contract contract() {
        return ApiTestBase.CONTRACT;
    }
}
