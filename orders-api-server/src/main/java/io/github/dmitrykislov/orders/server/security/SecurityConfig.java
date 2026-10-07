package io.github.dmitrykislov.orders.server.security;

import io.github.dmitrykislov.orders.server.config.ApiPathProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;

/**
 * Enforces the contract's {@code ApiKeyAuth} scheme with Spring Security for everything under the API
 * base path: stateless, no CSRF (no browser session to protect), no default login or Basic auth, and
 * authentication failures rendered as the contract's 401 problem by {@link ProblemAuthenticationEntryPoint}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ApiPathProperties apiPath,
            ApiKeyAuthenticationManager authenticationManager, ProblemAuthenticationEntryPoint entryPoint) throws Exception {
        AuthenticationFilter apiKeyFilter = new AuthenticationFilter(authenticationManager, new ApiKeyAuthenticationConverter());
        apiKeyFilter.setSuccessHandler((request, response, authentication) -> { /* continue the chain */ });
        apiKeyFilter.setFailureHandler(new AuthenticationEntryPointFailureHandler(entryPoint));

        http.securityMatcher(apiPath.basePath() + "/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)
                .headers(Customizer.withDefaults());
        return http.build();
    }
}
