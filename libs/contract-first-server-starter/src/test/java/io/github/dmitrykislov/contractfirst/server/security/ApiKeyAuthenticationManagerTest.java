package io.github.dmitrykislov.contractfirst.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dmitrykislov.contractfirst.server.ContractJson;
import io.github.dmitrykislov.contractfirst.server.ProblemFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.www.NonceExpiredException;
import tools.jackson.databind.json.JsonMapper;

class ApiKeyAuthenticationManagerTest {

    private final ApiKeyAuthenticationManager manager = new ApiKeyAuthenticationManager(Set.of("good-1", "good-2"));

    @Test
    void acceptsKnownKeysRejectsUnknownAndIgnoresOtherAuthentications() {
        Authentication verified = manager.authenticate(ApiKeyAuthentication.presented("good-2"));
        assertThat(verified.isAuthenticated()).isTrue();
        assertThat(verified.getCredentials()).isEqualTo("");
        assertThat(verified.getAuthorities()).extracting("authority").containsExactly(ApiKeyAuthentication.ROLE);

        assertThatThrownBy(() -> manager.authenticate(ApiKeyAuthentication.presented("good-")))
                .isInstanceOf(BadCredentialsException.class);

        Authentication other = new UsernamePasswordAuthenticationToken("u", "p");
        assertThat(manager.authenticate(other)).isSameAs(other);
    }

    @Test
    void refusesToExistWithoutKeys() {
        assertThatThrownBy(() -> new ApiKeyAuthenticationManager(Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void converterReadsTheConfiguredHeaderOnly() {
        ApiKeyAuthenticationConverter converter = new ApiKeyAuthenticationConverter("X-Api-Token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(converter.convert(request)).isNull();
        request.addHeader("X-Api-Token", "abc");
        assertThat(converter.convert(request)).extracting(Authentication::getCredentials).isEqualTo("abc");
    }

    @Test
    void entryPointWritesAProblemWithTheRightDetail() throws Exception {
        JsonMapper mapper = JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
        ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(
                new ContractJson(mapper), new ProblemFactory("urn:problem-type:"), "X-API-Key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");

        MockHttpServletResponse missing = new MockHttpServletResponse();
        entryPoint.commence(request, missing, new NonceExpiredException("not attempted"));
        assertThat(missing.getStatus()).isEqualTo(401);
        assertThat(missing.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(missing.getContentAsString()).contains("\"detail\":\"Missing X-API-Key header\"").contains("\"status\":401")
                .contains("\"type\":\"urn:problem-type:unauthorized\"");

        MockHttpServletResponse wrong = new MockHttpServletResponse();
        entryPoint.commence(request, wrong, new BadCredentialsException("bad"));
        assertThat(wrong.getContentAsString()).contains("Unrecognised API key").doesNotContain("bad\"");
    }
}
