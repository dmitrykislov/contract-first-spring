package io.github.dmitrykislov.orders.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

/** Edge cases of the advice that are awkward to provoke through MockMvc. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler(new ProblemFactory());

    @Test
    void nonStandardStatusCodesStillProduceAProblem() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.setServerName("orders.test");
        ResponseStatusException ex = new ResponseStatusException(HttpStatusCode.valueOf(599), "vendor specific");

        ResponseEntity<Object> response = handler.handleException(ex, new ServletWebRequest(request));

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(599);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("HTTP 599");
        assertThat(problem.getType()).hasToString(ProblemFactory.TYPE_NAMESPACE + "http-599");
        assertThat(problem.getDetail()).isEqualTo("vendor specific");
        assertThat(problem.getInstance()).hasToString("http://orders.test/api/v1/orders");
    }
}
