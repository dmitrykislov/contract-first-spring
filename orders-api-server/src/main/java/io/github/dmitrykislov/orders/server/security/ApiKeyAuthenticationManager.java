package io.github.dmitrykislov.orders.server.security;

import io.github.dmitrykislov.orders.server.config.OrdersServerProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Verifies presented API keys against the configured set. Defining this bean also makes Spring Boot's
 * default in-memory user (and its generated password) back off.
 */
@Component
public class ApiKeyAuthenticationManager implements AuthenticationManager {

    private final Set<String> apiKeys;

    public ApiKeyAuthenticationManager(OrdersServerProperties properties) {
        this.apiKeys = Set.copyOf(properties.security().apiKeys());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof ApiKeyAuthentication presented) || presented.isAuthenticated()) {
            return authentication;
        }
        if (!isKnown(String.valueOf(presented.getCredentials()))) {
            throw new BadCredentialsException("Unrecognised API key");
        }
        return ApiKeyAuthentication.verified();
    }

    private boolean isKnown(String presented) {
        byte[] candidate = presented.getBytes(StandardCharsets.UTF_8);
        boolean match = false;
        for (String key : apiKeys) {
            // Constant-time comparison per key to avoid leaking key prefixes through timing.
            match |= MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), candidate);
        }
        return match;
    }
}
