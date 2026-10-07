package io.github.dmitrykislov.contractfirst.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class ContractTest {

    private final Contract contract = Contract.fromClasspath("openapi/sample.yaml", "/base");

    @Test
    void listsOperationsWithTheirDocumentedResponsesAndVersion() {
        assertThat(contract.version()).isEqualTo("2.3.0");
        assertThat(contract.operations()).extracting(ContractOperation::toString)
                .containsExactly("POST /things", "GET /things/{id}");
        assertThat(contract.operations()).filteredOn(op -> op.operationId().equals("getThing")).singleElement()
                .satisfies(op -> assertThat(op.responseCodes()).containsExactlyInAnyOrder("200", "404"));
        assertThat(contract.operations()).filteredOn(op -> op.operationId().equals("getThing")).singleElement()
                .extracting(op -> op.route("/base")).isEqualTo("GET /base/things/{id}");
    }

    @Test
    void matchesConcreteRequestsBelowTheBasePathOnly() {
        assertThat(contract.matching("GET", "/base/things/42")).map(ContractOperation::operationId).contains("getThing");
        assertThat(contract.matching("get", "/base/things/42")).isPresent();
        assertThat(contract.matching("DELETE", "/base/things/42")).isEmpty();
        assertThat(contract.matching("GET", "/other/things/42")).isEmpty();
    }

    @Test
    void sameResourceAndBasePathShareOneInstanceAndCoverage() {
        assertThat(Contract.fromClasspath("openapi/sample.yaml", "/base")).isSameAs(contract);
        assertThat(Contract.fromClasspath("openapi/sample.yaml", "/elsewhere")).isNotSameAs(contract);
    }

    @Test
    void interceptorValidatesRecordsAndReportsNonNumericResponsesAsCoveredByDefault() throws IOException {
        Contract fresh = Contract.fromClasspath("openapi/sample.yaml", "/fresh");
        ContractValidatingInterceptor interceptor = fresh.validatingInterceptor();

        MockClientHttpRequest ok = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/fresh/things/1"));
        interceptor.intercept(ok, new byte[0], (req, body) -> jsonResponse(HttpStatus.OK, "{\"id\":\"1\"}"));

        MockClientHttpRequest bad = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/fresh/things/2"));
        assertThatThrownBy(() -> interceptor.intercept(bad, new byte[0], (req, body) -> jsonResponse(HttpStatus.OK, "{}")))
                .isInstanceOf(ContractValidatingInterceptor.ContractViolationException.class)
                .hasMessageContaining("id");

        assertThat(fresh.coverage().uncovered()).containsExactly("POST /things -> 201", "GET /things/{id} -> 404");
    }

    private static MockClientHttpResponse jsonResponse(HttpStatus status, String body) {
        MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(), status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response;
    }
}
