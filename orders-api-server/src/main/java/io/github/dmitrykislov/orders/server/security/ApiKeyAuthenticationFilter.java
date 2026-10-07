package io.github.dmitrykislov.orders.server.security;

import io.github.dmitrykislov.orders.server.config.ApiPathProperties;
import io.github.dmitrykislov.orders.server.config.OrdersServerProperties;
import io.github.dmitrykislov.orders.server.web.ProblemFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Enforces the contract's {@code ApiKeyAuth} security scheme ({@code X-API-Key} header) for every
 * path under the API base path. Rejections are rendered as Spring's {@code ProblemDetail} (which Boot's
 * {@code JsonMapper} serialises in RFC 9457 shape) so that even 401s stay spec-conformant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final Set<String> apiKeys;
    private final JsonMapper jsonMapper;
    private final ProblemFactory problems;
    private final ApiPathProperties apiPath;

    public ApiKeyAuthenticationFilter(OrdersServerProperties properties, JsonMapper jsonMapper,
            ProblemFactory problems, ApiPathProperties apiPath) {
        this.apiKeys = Set.copyOf(properties.security().apiKeys());
        this.jsonMapper = jsonMapper;
        this.problems = problems;
        this.apiPath = apiPath;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !apiPath.covers(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || !isKnown(presented)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String detail = presented == null ? "Missing %s header".formatted(HEADER) : "Unrecognised API key";
            jsonMapper.writeValue(response.getOutputStream(),
                    problems.of(HttpStatus.UNAUTHORIZED, "Unauthorized", detail, request));
            return;
        }
        chain.doFilter(request, response);
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
