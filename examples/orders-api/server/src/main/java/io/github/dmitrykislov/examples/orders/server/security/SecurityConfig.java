package io.github.dmitrykislov.examples.orders.server.security;

import io.github.dmitrykislov.contractfirst.server.security.ApiKeySecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The contract's {@code ApiKeyAuth} scheme for everything under the API base path. The building
 * blocks come from {@code contract-first-server-starter} and are switched on by
 * {@code openapi.server.api-key.accepted-keys}; the chain itself stays here so this application decides
 * what else it protects.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ApiKeySecurityConfigurer apiKey) throws Exception {
        return apiKey.configure(http).build();
    }
}
