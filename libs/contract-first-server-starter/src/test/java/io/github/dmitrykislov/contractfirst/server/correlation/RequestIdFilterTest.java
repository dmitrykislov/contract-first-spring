package io.github.dmitrykislov.contractfirst.server.correlation;

import io.github.dmitrykislov.contractfirst.server.ContractServerProperties;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter(new ContractServerProperties.RequestId(true, "X-Request-Id", "requestId"));

    @Test
    void echoesAValidIdExposesItInMdcAndCleansUp() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", id);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInMdc.set(MDC.get("requestId"));
            }
        });

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
        assertThat(seenInMdc).hasValue(id);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void generatesAnIdWhenMissingAndNeverReflectsNonUuidInput() throws Exception {
        assertThat(UUID.fromString(RequestIdFilter.resolve(null))).isNotNull();
        String replaced = RequestIdFilter.resolve("<script>alert(1)</script>");
        assertThat(replaced).doesNotContain("<");
        assertThat(UUID.fromString(replaced)).isNotNull();
        assertThat(RequestIdFilter.resolve("  11111111-1111-1111-1111-111111111111 ")).isEqualTo("11111111-1111-1111-1111-111111111111");
    }
}
