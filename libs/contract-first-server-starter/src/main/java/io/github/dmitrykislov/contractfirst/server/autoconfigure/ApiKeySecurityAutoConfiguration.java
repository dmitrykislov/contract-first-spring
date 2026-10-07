package io.github.dmitrykislov.contractfirst.server.autoconfigure;

import io.github.dmitrykislov.contractfirst.server.security.ApiKeyAuthenticationManager;
import io.github.dmitrykislov.contractfirst.server.security.ProblemAuthenticationEntryPoint;
import io.github.dmitrykislov.contractfirst.server.security.ApiKeyAuthenticationConverter;
import io.github.dmitrykislov.contractfirst.server.security.ApiKeySecurityConfigurer;
import io.github.dmitrykislov.contractfirst.server.json.ContractJsonMapper;
import io.github.dmitrykislov.contractfirst.server.autoconfigure.ContractServerAutoConfiguration;
import io.github.dmitrykislov.contractfirst.server.ContractServerProperties;
import io.github.dmitrykislov.contractfirst.server.problem.ProblemFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * API-key building blocks, created only in a servlet application, when Spring Security is on the
 * classpath <em>and</em> {@code openapi.server.api-key.accepted-keys} holds at least one key
 * (comma-separated or as a list). A non-web context that happens to see the same properties, such as a
 * client-only test context, gets nothing. The application composes them into its own
 * {@code SecurityFilterChain} via {@link ApiKeySecurityConfigurer}. Defining the {@link AuthenticationManager}
 * bean also makes Spring Boot's default in-memory user back off.
 */
@AutoConfiguration(after = ContractServerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthenticationManager.class)
@Conditional(ApiKeysConfiguredCondition.class)
public class ApiKeySecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthenticationManager.class)
    ApiKeyAuthenticationManager apiKeyAuthenticationManager(ContractServerProperties properties) {
        return new ApiKeyAuthenticationManager(properties.apiKey().acceptedKeys());
    }

    @Bean
    @ConditionalOnMissingBean
    ApiKeyAuthenticationConverter apiKeyAuthenticationConverter(ContractServerProperties properties) {
        return new ApiKeyAuthenticationConverter(properties.apiKey().headerName());
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(ContractJsonMapper json, ProblemFactory problems,
            ContractServerProperties properties) {
        return new ProblemAuthenticationEntryPoint(json, problems, properties.apiKey().headerName());
    }

    @Bean
    @ConditionalOnMissingBean
    ApiKeySecurityConfigurer apiKeySecurity(ContractServerProperties properties, ApiKeyAuthenticationManager manager,
            ApiKeyAuthenticationConverter converter, ProblemAuthenticationEntryPoint entryPoint) {
        return new ApiKeySecurityConfigurer(properties.basePath(), manager, converter, entryPoint);
    }
}
