package io.github.dmitrykislov.orders.server.security;

import io.github.dmitrykislov.contractfirst.server.security.ApiKeySecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The contract's {@code ApiKeyAuth} scheme for everything under the API base path. The building
 * blocks come from {@code contract-first-server-support} and are switched on by
 * {@code contract-first.server.api-key.keys}; the chain itself stays here so this application decides
 * what else it protects.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ApiKeySecurity apiKey) throws Exception {
        return apiKey.configure(http).build();
    }
}
