package io.github.dmitrykislov.contractfirst.testing;

import java.util.Set;

/**
 * One operation of a contract: HTTP method, template path below the base path, operationId and the
 * response codes it documents.
 */
public record ContractOperation(String method, String path, String operationId, Set<String> responseCodes)
        implements Comparable<ContractOperation> {

    /** The operation keyed as it appears on the wire, e.g. {@code GET /api/v1/orders/{orderId}}. */
    public String route(String basePath) {
        return method + " " + basePath + path;
    }

    @Override
    public String toString() {
        return method + " " + path;
    }

    @Override
    public int compareTo(ContractOperation other) {
        int byPath = path.compareTo(other.path);
        return byPath != 0 ? byPath : method.compareTo(other.method);
    }
}
