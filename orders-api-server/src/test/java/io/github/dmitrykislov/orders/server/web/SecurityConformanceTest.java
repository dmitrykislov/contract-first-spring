package io.github.dmitrykislov.orders.server.web;

import static io.github.dmitrykislov.orders.testsupport.MockMvcContract.assertResponseConforms;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.dmitrykislov.orders.server.model.Problem;
import io.github.dmitrykislov.orders.server.support.ApiTestBase;
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

    @Test
    void unknownApiKeyIsRejected() {
        MvcTestResult result = mvc.get().uri(url("/orders")).header("X-API-Key", "nope").exchange();

        assertResponseConforms(result);
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(fromJson(result, Problem.class).getDetail()).doesNotContain("nope");
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
