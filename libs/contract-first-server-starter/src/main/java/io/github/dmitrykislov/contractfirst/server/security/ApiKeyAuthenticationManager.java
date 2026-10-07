package io.github.dmitrykislov.contractfirst.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Verifies presented API keys against a configured set in constant time per key. Refuses to exist
 * without keys: a server that accepted nothing would be a misconfiguration, not a security posture.
 */
public final class ApiKeyAuthenticationManager implements AuthenticationManager {

    private final Set<String> apiKeys;

    public ApiKeyAuthenticationManager(Set<String> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            throw new IllegalArgumentException("openapi.server.api-key.accepted-keys must contain at least one key");
        }
        this.apiKeys = Set.copyOf(apiKeys);
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
            match |= MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), candidate);
        }
        return match;
    }
}
