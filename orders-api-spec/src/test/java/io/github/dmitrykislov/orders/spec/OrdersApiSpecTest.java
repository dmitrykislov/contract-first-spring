package io.github.dmitrykislov.orders.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lint rules for the contract itself. These catch mistakes long before code generation or runtime:
 * a broken $ref, a missing operationId (which would produce an ugly generated method name), an
 * operation without an error response, and so on.
 */
class OrdersApiSpecTest {

    private static final String SPEC_LOCATION = "openapi/orders-api.yaml";

    private static OpenAPI openApi;
    private static SwaggerParseResult parseResult;

    @BeforeAll
    static void parseSpec() {
        URL specUrl = OrdersApiSpecTest.class.getClassLoader().getResource(SPEC_LOCATION);
        assertThat(specUrl).as("spec resource %s on the classpath", SPEC_LOCATION).isNotNull();

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setValidateExternalRefs(true);
        parseResult = new OpenAPIParser().readLocation(specUrl.toString(), null, options);
        openApi = parseResult.getOpenAPI();
    }

    @Test
    @DisplayName("spec parses without any validation messages")
    void specIsValid() {
        assertThat(parseResult.getMessages()).as("parser messages").isEmpty();
        assertThat(openApi).isNotNull();
        assertThat(openApi.getInfo().getTitle()).isEqualTo("Orders API");
    }

    @Test
    @DisplayName("every operation has a unique operationId and at least one tag")
    void everyOperationIsIdentifiedAndTagged() {
        Set<String> seen = new HashSet<>();
        operations().forEach(op -> {
            assertThat(op.getOperationId()).as("operationId on %s", op.getSummary()).isNotBlank();
            assertThat(seen.add(op.getOperationId())).as("duplicate operationId %s", op.getOperationId()).isTrue();
            assertThat(op.getTags()).as("tags on %s", op.getOperationId()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("every operation documents a 401 response because the API is key-protected")
    void everyOperationDocumentsUnauthorized() {
        operations().forEach(op -> assertThat(op.getResponses())
                .as("responses of %s", op.getOperationId())
                .containsKey("401"));
    }

    @Test
    @DisplayName("every error response is a problem+json Problem")
    void errorResponsesAreProblemDetails() {
        operations().forEach(op -> op.getResponses().forEach((code, response) -> {
            if (code.startsWith("4") || code.startsWith("5")) {
                ApiResponse resolved = resolve(response);
                assertThat(resolved.getContent())
                        .as("%s %s content", op.getOperationId(), code)
                        .containsKey("application/problem+json");
                assertThat(resolved.getContent().get("application/problem+json").getSchema().get$ref())
                        .as("%s %s schema", op.getOperationId(), code)
                        .isEqualTo("#/components/schemas/Problem");
            }
        }));
    }

    @Test
    @DisplayName("the artifact version matches the contract's info.version")
    void artifactVersionMatchesContractVersion() throws java.io.IOException {
        java.util.Properties props = new java.util.Properties();
        try (var in = getClass().getClassLoader().getResourceAsStream("contract.properties")) {
            assertThat(in).as("filtered contract.properties").isNotNull();
            props.load(in);
        }
        String artifactVersion = props.getProperty("contract.artifact.version").replace("-SNAPSHOT", "");
        assertThat(openApi.getInfo().getVersion())
                .as("info.version must equal the Maven version (without -SNAPSHOT) so consumers can read the contract version off the jar")
                .isEqualTo(artifactVersion);
    }

    @Test
    @DisplayName("a global API key security requirement is declared")
    void apiKeySecurityIsDeclared() {
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("ApiKeyAuth");
        assertThat(openApi.getSecurity()).anySatisfy(req -> assertThat(req).containsKey("ApiKeyAuth"));
    }

    private static Stream<Operation> operations() {
        return openApi.getPaths().values().stream()
                .map(PathItem::readOperationsMap)
                .map(Map::values)
                .flatMap(Collection::stream);
    }

    private static ApiResponse resolve(ApiResponse response) {
        if (response.get$ref() == null) {
            return response;
        }
        String name = response.get$ref().substring(response.get$ref().lastIndexOf('/') + 1);
        ApiResponse resolved = openApi.getComponents().getResponses().get(name);
        assertThat(resolved).as("component response %s", name).isNotNull();
        return resolved;
    }
}
