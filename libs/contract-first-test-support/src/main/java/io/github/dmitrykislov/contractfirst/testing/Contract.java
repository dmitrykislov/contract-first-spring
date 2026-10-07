package io.github.dmitrykislov.contractfirst.testing;

import io.github.dmitrykislov.contractfirst.testing.restclient.ContractValidatingInterceptor;
import io.github.dmitrykislov.contractfirst.testing.mockmvc.MockMvcContractAssertions;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * An OpenAPI document under test: where it is, how it is mounted, a validator for it, its operations,
 * and the record of which documented responses the suite has exercised.
 *
 * <p>Instances are cached per (resource, base path), so every test class in a JVM that asks for the same
 * contract shares one validator and one coverage record.
 */
public final class Contract {

    private static final Map<String, Contract> INSTANCES = new ConcurrentHashMap<>();
    private static final PathPatternParser PARSER = new PathPatternParser();

    private final String resource;
    private final String basePath;
    private final URL url;
    private final OpenAPI api;
    private final OpenApiInteractionValidator validator;
    private final Set<ContractOperation> operations;
    private final ContractCoverage coverage = new ContractCoverage(this);

    private Contract(String resource, String basePath) {
        this.resource = resource;
        this.basePath = basePath;
        this.url = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource(resource),
                () -> "contract " + resource + " is missing from the classpath");
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        this.api = new OpenAPIParser().readLocation(url.toString(), null, options).getOpenAPI();
        this.validator = OpenApiInteractionValidator.createForSpecificationUrl(url.toString())
                .withBasePathOverride(basePath)
                .build();
        this.operations = api.getPaths().entrySet().stream()
                .flatMap(entry -> entry.getValue().readOperationsMap().entrySet().stream()
                        .map(op -> new ContractOperation(op.getKey().name(), entry.getKey(), op.getValue().getOperationId(),
                                Set.copyOf(op.getValue().getResponses().keySet()))))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * @param resource classpath location of the YAML or JSON document
     * @param basePath path part of {@code servers[0].url}; requests are matched against the contract below it
     */
    public static Contract fromClasspath(String resource, String basePath) {
        return INSTANCES.computeIfAbsent(resource + "@" + basePath, key -> new Contract(resource, basePath));
    }

    public String resource() {
        return resource;
    }

    public String basePath() {
        return basePath;
    }

    public URL url() {
        return url;
    }

    /** {@code info.version} of the document. */
    public String version() {
        return api.getInfo().getVersion();
    }

    public OpenApiInteractionValidator validator() {
        return validator;
    }

    public Set<ContractOperation> operations() {
        return operations;
    }

    /** The operation a concrete request matches, e.g. {@code GET /api/v1/orders/123...}. */
    public Optional<ContractOperation> matching(String httpMethod, String requestPath) {
        if (!requestPath.startsWith(basePath)) {
            return Optional.empty();
        }
        PathContainer relative = PathContainer.parsePath(requestPath.substring(basePath.length()));
        return operations.stream()
                .filter(op -> op.method().equalsIgnoreCase(httpMethod))
                .filter(op -> PARSER.parse(op.path()).matches(relative))
                .findFirst();
    }

    public ContractCoverage coverage() {
        return coverage;
    }

    /** Assertions for MockMvc results against this contract. */
    public MockMvcContractAssertions mockMvc() {
        return new MockMvcContractAssertions(this);
    }

    /** A {@code RestClient} interceptor that validates every real exchange against this contract. */
    public ContractValidatingInterceptor validatingInterceptor() {
        return new ContractValidatingInterceptor(this);
    }
}
