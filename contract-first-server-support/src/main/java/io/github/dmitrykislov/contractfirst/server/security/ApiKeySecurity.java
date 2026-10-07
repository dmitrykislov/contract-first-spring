package io.github.dmitrykislov.contractfirst.server.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;

/**
 * Configures an {@link HttpSecurity} as a stateless API-key chain: no CSRF, sessions, login or Basic
 * challenge; every request below the base path must present a valid key; failures are problems.
 *
 * <p>Deliberately not a {@code SecurityFilterChain} bean of its own: as soon as any chain bean exists
 * Spring Boot's default chain backs off, so the application must stay in charge of what else it
 * protects. Typical use:
 *
 * <pre>
 * &#64;Bean
 * SecurityFilterChain api(HttpSecurity http, ApiKeySecurity apiKey) throws Exception {
 *     return apiKey.configure(http).build();
 * }
 * </pre>
 */
public final class ApiKeySecurity {

    private final String basePath;
    private final ApiKeyAuthenticationManager manager;
    private final ApiKeyAuthenticationConverter converter;
    private final ProblemAuthenticationEntryPoint entryPoint;

    public ApiKeySecurity(String basePath, ApiKeyAuthenticationManager manager,
            ApiKeyAuthenticationConverter converter, ProblemAuthenticationEntryPoint entryPoint) {
        this.basePath = basePath;
        this.manager = manager;
        this.converter = converter;
        this.entryPoint = entryPoint;
    }

    public HttpSecurity configure(HttpSecurity http) throws Exception {
        AuthenticationFilter filter = new AuthenticationFilter(manager, converter);
        filter.setSuccessHandler((request, response, authentication) -> { /* continue the chain */ });
        filter.setFailureHandler(new AuthenticationEntryPointFailureHandler(entryPoint));

        return http.securityMatcher("/".equals(basePath) ? "/**" : basePath + "/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
                .headers(Customizer.withDefaults());
    }
}
