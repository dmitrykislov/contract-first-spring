package io.github.dmitrykislov.contractfirst.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records every (operation, response status) pair that passed through {@link MockMvcContract} or
 * {@link ContractValidatingInterceptor} for one {@link Contract}, so a final test can prove that each
 * response the contract documents was actually exercised, not merely declared.
 */
public final class ContractCoverage {

    private final Contract contract;
    private final Map<ContractOperation, Set<Integer>> observed = new ConcurrentHashMap<>();

    ContractCoverage(Contract contract) {
        this.contract = contract;
    }

    void record(String httpMethod, String requestPath, int status) {
        contract.matching(httpMethod, requestPath)
                .ifPresent(op -> observed.computeIfAbsent(op, k -> ConcurrentHashMap.newKeySet()).add(status));
    }

    public Map<ContractOperation, Set<Integer>> observed() {
        return Collections.unmodifiableMap(observed);
    }

    public boolean isEmpty() {
        return observed.isEmpty();
    }

    /** Documented responses nobody has exercised yet, as {@code "POST /orders -> 422"}. */
    public List<String> uncovered() {
        List<String> gaps = new ArrayList<>();
        for (ContractOperation op : contract.operations()) {
            Set<Integer> seen = observed.getOrDefault(op, Set.of());
            op.responseCodes().stream().sorted()
                    .filter(code -> code.chars().allMatch(Character::isDigit))
                    .filter(code -> !seen.contains(Integer.parseInt(code)))
                    .forEach(code -> gaps.add(op + " -> " + code));
        }
        return gaps;
    }
}
