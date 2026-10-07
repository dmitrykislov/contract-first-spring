package io.github.dmitrykislov.orders.server.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.dmitrykislov.contractfirst.testing.Contract;
import io.github.dmitrykislov.contractfirst.testing.ContractCoverage;
import io.github.dmitrykislov.orders.spec.OrdersContract;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Runs after all other test classes (see {@code junit-platform.properties}) and fails if any response
 * the contract documents, for any operation, was never produced and validated by a conformance test.
 * This is what makes "the tests cover the spec" a checked statement rather than a claim.
 */
@Order(Integer.MAX_VALUE)
class DocumentedResponsesCoverageTest {

    @Test
    void everyDocumentedResponseOfEveryOperationWasExercised() {
        ContractCoverage coverage = Contract.fromClasspath(OrdersContract.RESOURCE, OrdersContract.BASE_PATH).coverage();
        assumeFalse(coverage.isEmpty(), "no exchanges recorded: run the whole server test suite, not this class alone");

        assertThat(coverage.uncovered())
                .as("documented responses no conformance test produced")
                .isEmpty();
    }
}
