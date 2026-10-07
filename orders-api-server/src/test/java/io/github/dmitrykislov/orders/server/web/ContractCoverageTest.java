package io.github.dmitrykislov.orders.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.orders.server.support.ApiTestBase;
import io.github.dmitrykislov.orders.spec.OrdersContract;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The compiler guarantees every generated interface method is implemented, but not that every
 * contract operation ended up as a route (a generator option or package rename could silently drop
 * one). This compares the contract's operations with what Spring MVC actually mapped.
 */
class ContractCoverageTest extends ApiTestBase {

    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyContractOperationIsMappedExactlyOnce() {
        Set<String> expected = new TreeSet<>();
        CONTRACT.operations().forEach(op -> expected.add(op.route(OrdersContract.BASE_PATH)));

        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues() : Set.of();
            for (String pattern : patterns) {
                if (!pattern.startsWith(OrdersContract.BASE_PATH)) {
                    continue; // actuator, error page, ...
                }
                info.getMethodsCondition().getMethods().forEach(method -> actual.add(method.name() + " " + pattern));
            }
        }

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }
}
