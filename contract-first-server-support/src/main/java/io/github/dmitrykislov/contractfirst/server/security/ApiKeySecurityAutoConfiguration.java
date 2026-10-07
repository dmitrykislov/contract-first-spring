package io.github.dmitrykislov.contractfirst.server.security;

import io.github.dmitrykislov.contractfirst.server.ContractJson;
import io.github.dmitrykislov.contractfirst.server.ContractServerAutoConfiguration;
import io.github.dmitrykislov.contractfirst.server.ContractServerProperties;
import io.github.dmitrykislov.contractfirst.server.ProblemFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * API-key building blocks, created only when Spring Security is on the classpath <em>and</em>
 * {@code contract-first.server.api-key.keys} is configured. The application composes them into its own
 * {@code SecurityFilterChain} via {@link ApiKeySecurity}. Defining the {@link AuthenticationManager}
 * bean also makes Spring Boot's default in-memory user back off.
 */
@AutoConfiguration(after = ContractServerAutoConfiguration.class)
@ConditionalOnClass(AuthenticationManager.class)
@ConditionalOnProperty(name = ContractServerProperties.PREFIX + ".api-key.keys")
public class ApiKeySecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthenticationManager.class)
    ApiKeyAuthenticationManager apiKeyAuthenticationManager(ContractServerProperties properties) {
        return new ApiKeyAuthenticationManager(properties.apiKey().keys());
    }

    @Bean
    @ConditionalOnMissingBean
    ApiKeyAuthenticationConverter apiKeyAuthenticationConverter(ContractServerProperties properties) {
        return new ApiKeyAuthenticationConverter(properties.apiKey().headerName());
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(ContractJson json, ProblemFactory problems,
            ContractServerProperties properties) {
        return new ProblemAuthenticationEntryPoint(json, problems, properties.apiKey().headerName());
    }

    @Bean
    @ConditionalOnMissingBean
    ApiKeySecurity apiKeySecurity(ContractServerProperties properties, ApiKeyAuthenticationManager manager,
            ApiKeyAuthenticationConverter converter, ProblemAuthenticationEntryPoint entryPoint) {
        return new ApiKeySecurity(properties.basePath(), manager, converter, entryPoint);
    }
}
