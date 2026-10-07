package io.github.dmitrykislov.contractfirst.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RequestIdPropagationInterceptorTest {

    private final RequestIdPropagationInterceptor interceptor =
            new RequestIdPropagationInterceptor(new RequestIdProperties(true, "X-Request-Id", "requestId"));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesTheMdcValueWhenNoHeaderWasSet() throws IOException {
        MDC.put("requestId", "11111111-1111-1111-1111-111111111111");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://api/x"));

        interceptor.intercept(request, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst("X-Request-Id")).isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void anExplicitHeaderWins() throws IOException {
        MDC.put("requestId", "from-mdc");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://api/x"));
        request.getHeaders().set("X-Request-Id", "explicit");

        interceptor.intercept(request, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst("X-Request-Id")).isEqualTo("explicit");
    }

    @Test
    void doesNothingWithoutAnMdcValue() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://api/x"));

        interceptor.intercept(request, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().containsHeader("X-Request-Id")).isFalse();
    }
}
