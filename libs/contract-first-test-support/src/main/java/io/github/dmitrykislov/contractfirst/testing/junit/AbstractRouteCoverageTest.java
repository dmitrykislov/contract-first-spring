package io.github.dmitrykislov.contractfirst.testing.junit;

import io.github.dmitrykislov.contractfirst.testing.Contract;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Proves that Spring MVC maps every operation of the contract, exactly once, under the base path.
 * The compiler guarantees that every generated interface method is implemented, not that every
 * operation became a route; a generator option or package rename could silently drop one.
 *
 * <pre>
 * class ContractCoverageTest extends AbstractRouteCoverageTest {
 *     &#64;Autowired RequestMappingHandlerMapping mapping;
 *     protected Contract contract() { return CONTRACT; }
 *     protected RequestMappingHandlerMapping handlerMapping() { return mapping; }
 * }
 * </pre>
 */
public abstract class AbstractRouteCoverageTest {

    protected abstract Contract contract();

    protected abstract RequestMappingHandlerMapping handlerMapping();

    @Test
    void everyContractOperationIsMappedExactlyOnce() {
        Contract contract = contract();
        Set<String> expected = new TreeSet<>();
        contract.operations().forEach(op -> expected.add(op.route(contract.basePath())));

        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping().getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues() : Set.of();
            for (String pattern : patterns) {
                if (!pattern.startsWith(contract.basePath())) {
                    continue; // actuator, error page, other APIs
                }
                info.getMethodsCondition().getMethods().forEach(method -> actual.add(method.name() + " " + pattern));
            }
        }

        assertThat(actual).as("routes mapped below %s", contract.basePath()).containsExactlyInAnyOrderElementsOf(expected);
    }
}
