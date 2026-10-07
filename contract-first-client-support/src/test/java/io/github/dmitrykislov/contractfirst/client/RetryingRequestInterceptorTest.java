package io.github.dmitrykislov.contractfirst.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RetryingRequestInterceptorTest {

    private static final String URL = "http://api/things";

    private final List<Long> pauses = new ArrayList<>();
    private final RetryProperties threeAttempts = new RetryProperties(true, 3, Duration.ofMillis(100), 2.0,
            Duration.ofSeconds(1), Set.of(502, 503, 504), "Idempotency-Key");

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    private RestClient client(RetryProperties properties) {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        builder.requestInterceptor(new RetryingRequestInterceptor(properties, pauses::add));
        return builder.build();
    }

    @Test
    void retriesTransientStatusesOnGetWithExponentialBackoff() {
        RestClient client = client(threeAttempts);
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        String body = client.get().uri(URL).retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        assertThat(pauses).containsExactly(100L, 200L);
        server.verify();
    }

    @Test
    void returnsTheLastResponseWhenAttemptsAreExhausted() {
        RestClient client = client(threeAttempts);
        for (int i = 0; i < 3; i++) {
            server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        assertThatThrownBy(() -> client.get().uri(URL).retrieve().body(String.class))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        server.verify();
    }

    @Test
    void retriesConnectionFailuresAndRethrowsTheLastOne() {
        RestClient client = client(threeAttempts);
        server.expect(once(), requestTo(URL)).andRespond(withException(new IOException("reset")));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        assertThat(client.get().uri(URL).retrieve().body(String.class)).isEqualTo("ok");
        server.verify();

        RestClient failing = client(threeAttempts);
        for (int i = 0; i < 3; i++) {
            server.expect(once(), requestTo(URL)).andRespond(withException(new IOException("reset " + i)));
        }
        assertThatThrownBy(() -> failing.get().uri(URL).retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class).hasMessageContaining("reset 2");
        server.verify();
    }

    @Test
    void doesNotRetryPostOrPatchWithoutAnIdempotencyKey() {
        RestClient client = client(threeAttempts);
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.post().uri(URL).retrieve().toBodilessEntity()).isInstanceOf(HttpServerErrorException.class);
        assertThatThrownBy(() -> client.patch().uri(URL).retrieve().toBodilessEntity()).isInstanceOf(HttpServerErrorException.class);
        assertThat(pauses).isEmpty();
        server.verify();
    }

    @Test
    void retriesPostThatCarriesAnIdempotencyKey() {
        RestClient client = client(threeAttempts);
        server.expect(once(), requestTo(URL)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.CREATED));

        var response = client.post().uri(URL).header("Idempotency-Key", "idem-1").retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        server.verify();
    }

    @Test
    void nonTransientStatusesAndDisabledRetriesPassStraightThrough() {
        RestClient client = client(threeAttempts);
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.get().uri(URL).retrieve().toBodilessEntity()).isInstanceOf(HttpServerErrorException.class);
        server.verify();

        RestClient disabled = client(new RetryProperties(false, 3, Duration.ofMillis(1), 2.0, Duration.ofSeconds(1), Set.of(503), "Idempotency-Key"));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> disabled.get().uri(URL).retrieve().toBodilessEntity()).isInstanceOf(HttpServerErrorException.class);
        assertThat(pauses).isEmpty();
        server.verify();
    }

    @Test
    void rejectsNonsensicalSettings() {
        assertThatThrownBy(() -> new RetryProperties(true, 0, Duration.ofMillis(1), 2.0, Duration.ofSeconds(1), Set.of(), "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryProperties(true, 3, Duration.ofMillis(1), 0.5, Duration.ofSeconds(1), Set.of(), "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RetryProperties(true, 1, Duration.ofMillis(1), 2.0, Duration.ofSeconds(1), Set.of(), "k").active()).isFalse();
    }
}
