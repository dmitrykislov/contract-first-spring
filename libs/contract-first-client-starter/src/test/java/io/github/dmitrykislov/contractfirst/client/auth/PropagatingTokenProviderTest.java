package io.github.dmitrykislov.contractfirst.client.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PropagatingTokenProviderTest {

    private final AuthProperties apiKey = new AuthProperties(AuthProperties.Mode.PROPAGATE, "X-API-Key", "", null);
    private final AuthProperties bearer = new AuthProperties(AuthProperties.Mode.PROPAGATE, "Authorization", "Bearer", null);

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void emptyWhenNothingIsBound() {
        assertThat(new PropagatingTokenProvider(apiKey).token()).isEmpty();
    }

    @Test
    void readsTheExplicitContextFirst() {
        incoming("X-API-Key", "from-request");
        assertThat(TokenContext.with("explicit", () -> new PropagatingTokenProvider(apiKey).token())).contains("explicit");
        assertThat(new PropagatingTokenProvider(apiKey).token()).contains("from-request");
    }

    @Test
    void readsTheServletHeaderAndStripsTheScheme() {
        incoming("Authorization", "bearer jwt-value");
        assertThat(new PropagatingTokenProvider(bearer).token()).contains("jwt-value");
    }

    @Test
    void ignoresBlankHeaders() {
        incoming("X-API-Key", "   ");
        assertThat(new PropagatingTokenProvider(apiKey).token()).isEmpty();
    }

    @Test
    void contextIsScopedAndInheritedByNestedCalls() {
        String seen = TokenContext.with("outer", () -> {
            String inner = TokenContext.with("inner", () -> TokenContext.current().orElseThrow());
            return inner + "/" + TokenContext.current().orElseThrow();
        });
        assertThat(seen).isEqualTo("inner/outer");
        assertThat(TokenContext.current()).isEmpty();
    }

    private static void incoming(String header, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(header, value);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
