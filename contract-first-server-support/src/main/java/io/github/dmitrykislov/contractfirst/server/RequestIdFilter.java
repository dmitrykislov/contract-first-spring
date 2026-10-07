package io.github.dmitrykislov.contractfirst.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Honours a correlation header: echoes a well-formed client value or generates one, exposes it as a
 * response header and in the logging MDC (where {@code contract-first-client-support} picks it up for
 * outgoing calls). Never reflects arbitrary input: a value that is not a UUID is replaced.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    private final ContractServerProperties.RequestId settings;

    public RequestIdFilter(ContractServerProperties.RequestId settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(settings.headerName()));
        response.setHeader(settings.headerName(), requestId);
        MDC.put(settings.mdcKey(), requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(settings.mdcKey());
        }
    }

    static String resolve(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        try {
            return UUID.fromString(incoming.trim()).toString();
        } catch (IllegalArgumentException notAUuid) {
            return UUID.randomUUID().toString();
        }
    }
}
