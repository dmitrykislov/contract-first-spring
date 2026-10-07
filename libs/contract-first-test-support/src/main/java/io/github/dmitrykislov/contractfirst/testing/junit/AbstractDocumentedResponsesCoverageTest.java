package io.github.dmitrykislov.contractfirst.testing.junit;

import io.github.dmitrykislov.contractfirst.testing.ContractCoverage;
import io.github.dmitrykislov.contractfirst.testing.Contract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.Test;

/**
 * Fails if any response the contract documents, for any operation, was never produced and validated
 * by a conformance test in this JVM. It must run after every other test class, so the subclass needs
 * {@code @Order(Integer.MAX_VALUE)} and the module needs {@code junit-platform.properties} with
 * {@code junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$OrderAnnotation}.
 *
 * <pre>
 * &#64;Order(Integer.MAX_VALUE)
 * class DocumentedResponsesCoverageTest extends AbstractDocumentedResponsesCoverageTest {
 *     protected Contract contract() { return CONTRACT; }
 * }
 * </pre>
 */
public abstract class AbstractDocumentedResponsesCoverageTest {

    protected abstract Contract contract();

    @Test
    void everyDocumentedResponseOfEveryOperationWasExercised() {
        ContractCoverage coverage = contract().coverage();
        assumeFalse(coverage.isEmpty(), "no exchanges recorded: run the whole test suite, not this class alone");

        assertThat(coverage.uncovered())
                .as("documented responses no conformance test produced")
                .isEmpty();
    }
}
