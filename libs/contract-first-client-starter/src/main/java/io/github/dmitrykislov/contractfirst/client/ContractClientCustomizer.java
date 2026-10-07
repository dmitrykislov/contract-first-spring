package io.github.dmitrykislov.contractfirst.client;

import io.github.dmitrykislov.contractfirst.client.correlation.RequestIdPropagationInterceptor;
import io.github.dmitrykislov.contractfirst.client.correlation.RequestIdProperties;
import io.github.dmitrykislov.contractfirst.client.retry.RetryingRequestInterceptor;
import io.github.dmitrykislov.contractfirst.client.retry.RetryProperties;
import io.github.dmitrykislov.contractfirst.client.errors.ProblemResponseErrorHandler;
import io.github.dmitrykislov.contractfirst.client.errors.ApiExceptionFactory;
import io.github.dmitrykislov.contractfirst.client.errors.ApiException;
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
 * Composes the runtime behaviour a generated contract client needs on top of Spring Boot's
 * property-driven {@code RestClient}: contract-safe JSON, per-request token resolution, correlation-id
 * propagation, retries for idempotent calls and {@link ProblemDetail}-aware error mapping.
 *
 * <p>Most applications never touch this class: {@link EnableContractClient} builds and applies it per
 * group from {@code openapi.clients.*}. Use it directly to give the same behaviour to a {@code RestClient}
 * that is not part of an HTTP service group, or in tests:
 *
 * <pre>
 * RestClient.Builder builder = RestClient.builder().baseUrl("https://billing.example.com/v1");
 * ContractClientCustomizer.forGroup("billing")
 *         .auth(authProperties, tokenProvider)
 *         .retry(retryProperties)
 *         .requestId(requestIdProperties)
 *         .jsonMapper(applicationJsonMapper)
 *         .exceptions(ApiExceptionFactory.forType(BillingApiException.class))
 *         .customize(builder);
 * </pre>
 *
 * <p>Interceptor order on the builder is retry (outermost), then request id, then token, so each
 * attempt re-resolves the token and keeps the correlation id.
 */
public final class ContractClientCustomizer {

    private final String group;
    private @Nullable AuthProperties auth;
    private @Nullable TokenProvider tokenProvider;
    private @Nullable RetryProperties retry;
    private @Nullable RequestIdProperties requestId;
    private @Nullable JsonMapper jsonMapper;
    private ApiExceptionFactory exceptions = ApiException::new;

    private ContractClientCustomizer(String group) {
        this.group = group;
    }

    public static ContractClientCustomizer forGroup(String group) {
        return new ContractClientCustomizer(Objects.requireNonNull(group, "group"));
    }

    /** The built-in token source for {@code static} and {@code propagate} modes; {@code provider} needs a bean. */
    public static TokenProvider tokenProvider(AuthProperties auth, String providerDescription) {
        return switch (auth.mode()) {
            case STATIC -> new StaticTokenProvider(Objects.requireNonNull(auth.token(), "validated by AuthProperties"));
            case PROPAGATE -> new PropagatingTokenProvider(auth);
            case PROVIDER -> throw new IllegalStateException(
                    "auth.mode=provider requires " + providerDescription + " that fetches the token, e.g. from your identity provider");
        };
    }

    public static TokenProvider tokenProvider(AuthProperties auth, Class<? extends TokenProvider> providerType) {
        return tokenProvider(auth, "a bean of type " + providerType.getName());
    }

    public ContractClientCustomizer auth(AuthProperties auth, TokenProvider tokenProvider) {
        this.auth = auth;
        this.tokenProvider = tokenProvider;
        return this;
    }

    public ContractClientCustomizer retry(RetryProperties retry) {
        this.retry = retry;
        return this;
    }

    public ContractClientCustomizer requestId(RequestIdProperties requestId) {
        this.requestId = requestId;
        return this;
    }

    /** The application's mapper; a contract-safe copy is derived from it so other customisations survive. */
    public ContractClientCustomizer jsonMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        return this;
    }

    public ContractClientCustomizer exceptions(ApiExceptionFactory factory) {
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
        builder.defaultStatusHandler(HttpStatusCode::isError, new ProblemResponseErrorHandler(group, mapper, exceptions));
    }

    /** The same customisation packaged as a Spring Boot HTTP service group configurer for {@code group}. */
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
