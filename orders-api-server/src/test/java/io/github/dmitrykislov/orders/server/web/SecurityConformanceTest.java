package io.github.dmitrykislov.orders.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.orders.server.model.Problem;
import io.github.dmitrykislov.orders.server.support.ApiTestBase;
import io.github.dmitrykislov.contractfirst.testing.ContractOperation;
import io.github.dmitrykislov.orders.spec.OrdersContract;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SecurityConformanceTest extends ApiTestBase {

    @Test
    void missingApiKeyIsRejectedWithProblem() {
        MvcTestResult result = mvc.get().uri(url("/orders")).exchange();

        assertResponseConforms(result);
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        Problem problem = fromJson(result, Problem.class);
        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).contains("X-API-Key");
    }

    /** The contract requires the key on every operation, so prove the 401 problem on every route. */
    @ParameterizedTest(name = "{0} without a key is a 401 problem")
    @MethodSource("allOperations")
    void everyOperationRejectsMissingApiKey(ContractOperation operation) {
        String uri = operation.route(OrdersContract.BASE_PATH).split(" ", 2)[1]
                .replace("{orderId}", randomId().toString())
                .replace("{sku}", "WIDGET-BLUE-L");
        MvcTestResult result = mvc.method(HttpMethod.valueOf(operation.method())).uri(uri)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        assertResponseConforms(result);
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    static Stream<ContractOperation> allOperations() {
        return CONTRACT.operations().stream();
    }

    @Test
    void unknownApiKeyIsRejected() {
        MvcTestResult result = mvc.get().uri(url("/orders")).header("X-API-Key", "nope").exchange();

        assertResponseConforms(result);
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(fromJson(result, Problem.class).getDetail()).doesNotContain("nope");
    }

    @Test
    void noBasicAuthChallengeOrSessionIsOffered() {
        MvcTestResult result = mvc.get().uri(url("/orders")).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void unauthorizedResponsesStillCarryRequestId() {
        MvcTestResult result = mvc.delete().uri(url("/orders/{id}"), randomId()).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void nonUuidRequestIdIsRejectedByTheContractAndNeverEchoed() {
        MvcTestResult result = mvc.get().uri(url("/orders")).headers(authenticated())
                .header("X-Request-Id", "<script>alert(1)</script>").exchange();

        // The header is typed uuid in the contract, so the value is a 400; the filter still answers
        // with a fresh correlation id rather than reflecting the input.
        assertResponseConforms(result);
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        String echoed = result.getResponse().getHeader("X-Request-Id");
        assertThat(echoed).isNotNull().doesNotContain("<");
        assertThat(java.util.UUID.fromString(echoed)).isNotNull();
    }

    @Test
    void unknownRouteUnderApiIsAProblemToo() {
        MvcTestResult result = mvc.get().uri(url("/nothing-here")).headers(authenticated()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
