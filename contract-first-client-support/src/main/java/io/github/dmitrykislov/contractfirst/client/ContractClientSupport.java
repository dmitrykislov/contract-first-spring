package io.github.dmitrykislov.contractfirst.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.dmitrykislov.contractfirst.client.auth.AuthProperties;
import io.github.dmitrykislov.contractfirst.client.auth.PropagatingTokenProvider;
import io.github.dmitrykislov.contractfirst.client.auth.StaticTokenProvider;
import io.github.dmitrykislov.contractfirst.client.auth.TokenHeaderInterceptor;
import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Composes the runtime behaviour every generated contract client needs on top of Spring Boot's
 * property-driven {@code RestClient}: contract-safe JSON, per-request token resolution, correlation-id
 * propagation, retries for idempotent calls and {@link ProblemDetail}-aware error mapping. An API's
 * auto-configuration uses it in a few lines:
 *
 * <pre>
 * &#64;Bean &#64;Order(Ordered.LOWEST_PRECEDENCE - 100)
 * RestClientHttpServiceGroupConfigurer ordersClientGroupConfigurer(OrdersClientProperties p, OrdersTokenProvider t, JsonMapper m) {
 *     return ContractClientSupport.forGroup("orders")
 *             .auth(p.auth(), t).retry(p.retry()).requestId(p.requestId())
 *             .jsonMapper(m).exceptions(OrdersApiException::new)
 *             .groupConfigurer();
 * }
 * </pre>
 *
 * <p>Interceptor order on the builder is retry (outermost), then request id, then token, so each
 * attempt re-resolves the token and keeps the correlation id.
 */
public final class ContractClientSupport {

    private final String group;
    private @Nullable AuthProperties auth;
    private @Nullable TokenProvider tokenProvider;
    private @Nullable RetryProperties retry;
    private @Nullable RequestIdProperties requestId;
    private @Nullable JsonMapper jsonMapper;
    private ApiExceptionFactory exceptions = ApiException::new;

    private ContractClientSupport(String group) {
        this.group = group;
    }

    public static ContractClientSupport forGroup(String group) {
        return new ContractClientSupport(Objects.requireNonNull(group, "group"));
    }

    /** The built-in token source for {@code static} and {@code propagate} modes; {@code provider} needs a bean. */
    public static TokenProvider tokenProvider(AuthProperties auth, Class<? extends TokenProvider> providerType) {
        return switch (auth.mode()) {
            case STATIC -> new StaticTokenProvider(Objects.requireNonNull(auth.token(), "validated by AuthProperties"));
            case PROPAGATE -> new PropagatingTokenProvider(auth);
            case PROVIDER -> throw new IllegalStateException(
                    "auth.mode=provider requires a bean of type " + providerType.getName()
                            + " that fetches the token, e.g. from your identity provider");
        };
    }

    public ContractClientSupport auth(AuthProperties auth, TokenProvider tokenProvider) {
        this.auth = auth;
        this.tokenProvider = tokenProvider;
        return this;
    }

    public ContractClientSupport retry(RetryProperties retry) {
        this.retry = retry;
        return this;
    }

    public ContractClientSupport requestId(RequestIdProperties requestId) {
        this.requestId = requestId;
        return this;
    }

    /** The application's mapper; a contract-safe copy is derived from it so other customisations survive. */
    public ContractClientSupport jsonMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        return this;
    }

    public ContractClientSupport exceptions(ApiExceptionFactory factory) {
        this.exceptions = factory;
        return this;
    }

    /** Applies everything configured to one {@code RestClient.Builder}; useful outside HTTP service groups and in tests. */
    public void customize(RestClient.Builder builder) {
        JsonMapper mapper = contractMapper(jsonMapper != null ? jsonMapper : JsonMapper.builder().build());
        builder.configureMessageConverters(converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)));
        if (retry != null && retry.active()) {
            builder.requestInterceptor(new RetryingRequestInterceptor(retry));
        }
        if (requestId != null && requestId.enabled()) {
            builder.requestInterceptor(new RequestIdPropagationInterceptor(requestId));
        }
        if (auth != null) {
            builder.requestInterceptor(new TokenHeaderInterceptor(
                    Objects.requireNonNull(tokenProvider, "tokenProvider"), auth));
        }
        builder.defaultStatusHandler(HttpStatusCode::isError, new ProblemResponseErrorHandler(mapper, exceptions));
    }

    /** The same customisation as a Spring Boot HTTP service group configurer for {@code group}. */
    public RestClientHttpServiceGroupConfigurer groupConfigurer() {
        return groups -> groups.filterByName(group).forEachClient((g, builder) -> customize(builder));
    }

    /**
     * Optional properties in a contract are not nullable, so unset fields must be omitted rather than
     * sent as {@code null}; {@code nullable: true} properties are generated as {@code JsonNullable}.
     * Pinned here regardless of the consumer's global Jackson settings.
     */
    public static JsonMapper contractMapper(JsonMapper base) {
        return base.rebuild()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(new JsonNullableJackson3Module())
                .build();
    }
}
