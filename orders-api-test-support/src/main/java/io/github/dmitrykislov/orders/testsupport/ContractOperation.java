package io.github.dmitrykislov.orders.testsupport;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * One operation of the contract: HTTP method, template path below the base path, operationId and the
 * response codes it documents. Used to prove that generated clients and server mappings cover every
 * operation, and that the test suites exercise every documented response.
 */
public record ContractOperation(String method, String path, String operationId, Set<String> responseCodes)
        implements Comparable<ContractOperation> {

    private static final PathPatternParser PARSER = new PathPatternParser();
    private static final Set<ContractOperation> ALL = load();

    public static Set<ContractOperation> all() {
        return ALL;
    }

    private static Set<ContractOperation> load() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        OpenAPI api = new OpenAPIParser().readLocation(OrdersContract.specUrl().toString(), null, options).getOpenAPI();
        return api.getPaths().entrySet().stream()
                .flatMap(entry -> entry.getValue().readOperationsMap().entrySet().stream()
                        .map(op -> new ContractOperation(op.getKey().name(), entry.getKey(), op.getValue().getOperationId(),
                                Set.copyOf(op.getValue().getResponses().keySet()))))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Finds the operation a concrete request matches, e.g. {@code GET /api/v1/orders/123...}. */
    public static java.util.Optional<ContractOperation> matching(String httpMethod, String requestPath, String basePath) {
        if (!requestPath.startsWith(basePath)) {
            return java.util.Optional.empty();
        }
        PathContainer relative = PathContainer.parsePath(requestPath.substring(basePath.length()));
        return ALL.stream()
                .filter(op -> op.method().equalsIgnoreCase(httpMethod))
                .filter(op -> op.pattern().matches(relative))
                .findFirst();
    }

    /** The same operation keyed as it appears on the wire, e.g. {@code GET /api/v1/orders/{orderId}}. */
    public String route(String basePath) {
        return method + " " + basePath + path;
    }

    private PathPattern pattern() {
        return PARSER.parse(path);
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
