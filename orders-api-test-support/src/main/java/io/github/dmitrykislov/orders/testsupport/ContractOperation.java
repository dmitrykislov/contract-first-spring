package io.github.dmitrykislov.orders.testsupport;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * One operation of the contract: HTTP method, template path below the base path and operationId.
 * Used to prove that generated clients and server mappings cover every operation, not just the ones a
 * test happens to call.
 */
public record ContractOperation(String method, String path, String operationId) implements Comparable<ContractOperation> {

    public static Set<ContractOperation> all() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        OpenAPI api = new OpenAPIParser().readLocation(OrdersContract.specUrl().toString(), null, options).getOpenAPI();
        return api.getPaths().entrySet().stream()
                .flatMap(entry -> entry.getValue().readOperationsMap().entrySet().stream()
                        .map(op -> new ContractOperation(op.getKey().name(), entry.getKey(), op.getValue().getOperationId())))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** The same operation keyed as it appears on the wire, e.g. {@code GET /api/v1/orders/{orderId}}. */
    public String route(String basePath) {
        return method + " " + basePath + path;
    }

    public static String route(PathItem.HttpMethod method, String fullPath) {
        return method.name() + " " + fullPath;
    }

    @Override
    public int compareTo(ContractOperation other) {
        int byPath = path.compareTo(other.path);
        return byPath != 0 ? byPath : method.compareTo(other.method);
    }
}
