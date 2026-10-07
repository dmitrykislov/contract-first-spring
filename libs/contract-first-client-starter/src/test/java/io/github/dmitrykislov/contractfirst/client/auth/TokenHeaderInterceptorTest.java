package io.github.dmitrykislov.contractfirst.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class TokenHeaderInterceptorTest {

    private final AuthProperties bearer = new AuthProperties(AuthProperties.Mode.PROVIDER, "Authorization", "Bearer", null);
    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://api/things"));
    private final AtomicBoolean executed = new AtomicBoolean();
    private final ClientHttpRequestExecution execution = (req, body) -> {
        executed.set(true);
        return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    };

    @Test
    void setsSchemeAndTokenOnTheConfiguredHeader() throws IOException {
        new TokenHeaderInterceptor(() -> Optional.of("t0k"), bearer).intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer t0k");
        assertThat(executed).isTrue();
    }

    @Test
    void failsBeforeSendingWhenThereIsNoToken() {
        assertThatThrownBy(() -> new TokenHeaderInterceptor(Optional::empty, bearer).intercept(request, new byte[0], execution))
                .isInstanceOf(ClientAuthenticationException.class)
                .hasMessageContaining("GET http://api/things")
                .hasMessageContaining("PROVIDER")
                .hasMessageContaining("empty token");
        assertThat(executed).isFalse();
    }

    @Test
    void wrapsProviderFailuresSoCallersHaveOneTypeToCatch() {
        IllegalStateException idpDown = new IllegalStateException("IdP unreachable");
        TokenProvider failing = () -> { throw idpDown; };

        assertThatThrownBy(() -> new TokenHeaderInterceptor(failing, bearer).intercept(request, new byte[0], execution))
                .isInstanceOf(ClientAuthenticationException.class)
                .hasMessageContaining("Token provider failed")
                .hasCause(idpDown);
        assertThat(executed).isFalse();
    }
}
