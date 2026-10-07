package io.github.dmitrykislov.contractfirst.testing.junit;

import io.github.dmitrykislov.contractfirst.testing.ContractOperation;
import io.github.dmitrykislov.contractfirst.testing.Contract;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * For a contract whose every operation requires credentials: sends each operation without any and
 * proves the answer is the documented rejection (401 by default) as a spec-conformant problem. Path
 * variables are filled with UUIDs, which satisfy both {@code uuid} and plain string parameters; override
 * {@link #samplePathValue} for stricter patterns.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractUnauthenticatedRequestsTest {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)}");

    protected abstract Contract contract();

    protected abstract MockMvcTester mvc();

    protected HttpStatus expectedStatus() {
        return HttpStatus.UNAUTHORIZED;
    }

    protected String samplePathValue(String variableName) {
        return UUID.randomUUID().toString();
    }

    Stream<ContractOperation> operations() {
        return contract().operations().stream();
    }

    @ParameterizedTest(name = "{0} without credentials")
    @MethodSource("operations")
    void everyOperationRejectsUnauthenticatedRequests(ContractOperation operation) {
        String uri = contract().basePath() + fillPathVariables(operation.path());
        MvcTestResult result = mvc().method(HttpMethod.valueOf(operation.method())).uri(uri)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        contract().mockMvc().assertResponseConforms(result);
        assertThat(result).hasStatus(expectedStatus()).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private String fillPathVariables(String template) {
        Matcher matcher = PATH_VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(samplePathValue(matcher.group(1))));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
