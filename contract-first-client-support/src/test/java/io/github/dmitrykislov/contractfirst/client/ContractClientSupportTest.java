package io.github.dmitrykislov.contractfirst.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.dmitrykislov.contractfirst.client.auth.AuthProperties;
import io.github.dmitrykislov.contractfirst.client.auth.PropagatingTokenProvider;
import io.github.dmitrykislov.contractfirst.client.auth.StaticTokenProvider;
import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** The whole composition on one RestClient, as an API's auto-configuration would apply it to a group. */
class ContractClientSupportTest {

    record Payload(@Nullable String optional, String required, JsonNullable<String> nullable) {}

    static final class ThingsApiException extends ApiException {
        ThingsApiException(HttpStatusCode status, @Nullable ProblemDetail problem, List<ApiFieldError> errors, @Nullable String raw) {
            super(status, problem, errors, raw);
        }
    }

    private final JsonMapper appMapper = JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    private final AuthProperties auth = new AuthProperties(AuthProperties.Mode.STATIC, "Authorization", "Bearer", "secret");
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private RestClient client() {
        ContractClientSupport.forGroup("things")
                .auth(auth, new StaticTokenProvider("secret"))
                .retry(new RetryProperties(true, 2, Duration.ofMillis(1), 1.0, Duration.ofMillis(1), Set.of(503), "Idempotency-Key"))
                .requestId(new RequestIdProperties(true, "X-Request-Id", "requestId"))
                .jsonMapper(appMapper)
                .exceptions(ThingsApiException::new)
                .customize(builder);
        return builder.build();
    }

    @Test
    void appliesTokenRequestIdJsonPolicyAndRetriesTogether() {
        MDC.put("requestId", "corr-1");
        server.expect(once(), requestTo("http://api/things"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(header("X-Request-Id", "corr-1"))
                .andExpect(content().json("{\"required\":\"r\",\"nullable\":null}", true))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo("http://api/things"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andRespond(withSuccess("{\"required\":\"r\",\"nullable\":\"n\"}", MediaType.APPLICATION_JSON));

        Payload echoed = client().put().uri("http://api/things")
                .body(new Payload(null, "r", JsonNullable.of(null)))
                .retrieve().body(Payload.class);

        assertThat(echoed).isNotNull();
        assertThat(echoed.optional()).isNull();
        assertThat(echoed.nullable().get()).isEqualTo("n");
        server.verify();
    }

    @Test
    void mapsErrorsThroughTheConfiguredExceptionType() {
        server.expect(once(), requestTo("http://api/things/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Not Found\",\"status\":404,\"detail\":\"no thing 1\"}"));

        assertThatThrownBy(() -> client().get().uri("http://api/things/1").retrieve().body(String.class))
                .isInstanceOfSatisfying(ThingsApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.problem()).get().extracting(ProblemDetail::getDetail).isEqualTo("no thing 1");
                });
        server.verify();
    }

    @Test
    void builtInTokenProvidersFollowTheMode() {
        assertThat(ContractClientSupport.tokenProvider(auth, TokenProvider.class)).isInstanceOf(StaticTokenProvider.class);
        assertThat(ContractClientSupport.tokenProvider(new AuthProperties(AuthProperties.Mode.PROPAGATE, "X-API-Key", "", null), TokenProvider.class))
                .isInstanceOf(PropagatingTokenProvider.class);
        assertThatThrownBy(() -> ContractClientSupport.tokenProvider(new AuthProperties(AuthProperties.Mode.PROVIDER, "X-API-Key", "", null), TokenProvider.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(TokenProvider.class.getName());
    }

    @Test
    void contractMapperOmitsNullsAndKnowsJsonNullable() {
        JsonMapper mapper = ContractClientSupport.contractMapper(appMapper);
        assertThat(mapper.writeValueAsString(new Payload(null, "r", JsonNullable.undefined()))).isEqualTo("{\"required\":\"r\"}");
        assertThat(mapper.writeValueAsString(new Payload("o", "r", JsonNullable.of(null)))).isEqualTo("{\"optional\":\"o\",\"required\":\"r\",\"nullable\":null}");
    }
}
