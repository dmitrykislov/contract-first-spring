package io.github.dmitrykislov.contractfirst.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

class ContractExceptionHandlerTest {

    static final class NotFoundish extends RuntimeException {
        NotFoundish(String m) { super(m); }
    }

    private final ProblemFactory problems = new ProblemFactory("https://api.test/problems/");
    private final DomainExceptionMapper first = ex -> ex instanceof NotFoundish ? Optional.of(HttpStatus.NOT_FOUND) : Optional.empty();
    private final DomainExceptionMapper second = ex -> ex instanceof NotFoundish ? Optional.of(HttpStatus.GONE) : Optional.empty();
    private final ContractExceptionHandler handler = new ContractExceptionHandler(problems, List.of(first, second));
    private final MockHttpServletRequest request = request("/api/v1/things/1");

    @Test
    void firstMapperWithAnAnswerWinsAndExceptionMessageBecomesDetail() {
        ResponseEntity<ProblemDetail> response = handler.unmappedOrDomain(new NotFoundish("thing 1 is gone"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getDetail()).isEqualTo("thing 1 is gone");
        assertThat(problem.getType()).hasToString("https://api.test/problems/not-found");
        assertThat(problem.getInstance()).hasToString("http://api.test/api/v1/things/1");
    }

    @Test
    void unmappedExceptionsAreA500WithoutLeakingTheMessage() {
        ResponseEntity<ProblemDetail> response = handler.unmappedOrDomain(new IllegalStateException("db password is hunter2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred").doesNotContain("hunter2");
    }

    @Test
    void frameworkExceptionsKeepSpringsStatusAndGetDecorated() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new ResponseStatusException(HttpStatusCode.valueOf(599), "vendor specific"), new ServletWebRequest(request));

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(599);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("HTTP 599");
        assertThat(problem.getType()).hasToString("https://api.test/problems/http-599");
        assertThat(problem.getDetail()).isEqualTo("vendor specific");
        assertThat(problem.getInstance().isAbsolute()).isTrue();
    }

    @Test
    void isOrderedLastSoApplicationAdvicesWin() {
        assertThat(OrderUtils.getOrder(ContractExceptionHandler.class)).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServerName("api.test");
        return request;
    }
}
