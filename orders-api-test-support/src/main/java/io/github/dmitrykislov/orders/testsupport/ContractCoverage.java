package io.github.dmitrykislov.orders.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records every (operation, response status) pair that passed through {@link MockMvcContract} or
 * {@link ContractValidatingInterceptor} during a test run, so a final test can prove that each response
 * the contract documents was actually exercised, not merely declared.
 */
public final class ContractCoverage {

    private static final Map<ContractOperation, Set<Integer>> OBSERVED = new ConcurrentHashMap<>();

    private ContractCoverage() {}

    static void record(String httpMethod, String requestPath, int status) {
        ContractOperation.matching(httpMethod, requestPath, OrdersContract.BASE_PATH)
                .ifPresent(op -> OBSERVED.computeIfAbsent(op, k -> ConcurrentHashMap.newKeySet()).add(status));
    }

    public static Map<ContractOperation, Set<Integer>> observed() {
        return Collections.unmodifiableMap(OBSERVED);
    }

    /** Documented responses nobody has exercised yet, as {@code "POST /orders -> 422"}. */
    public static List<String> uncovered() {
        List<String> gaps = new ArrayList<>();
        for (ContractOperation op : ContractOperation.all()) {
            Set<Integer> seen = OBSERVED.getOrDefault(op, Set.of());
            op.responseCodes().stream().sorted()
                    .filter(code -> !seen.contains(Integer.parseInt(code)))
                    .forEach(code -> gaps.add(op + " -> " + code));
        }
        return gaps;
    }
}
