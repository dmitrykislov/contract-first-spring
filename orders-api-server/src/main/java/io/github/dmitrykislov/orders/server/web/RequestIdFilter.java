package io.github.dmitrykislov.orders.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Honours the contract's {@code X-Request-Id} correlation header: echoes a well-formed client value
 * or generates one, exposes it as a response header and in the logging MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        try {
            return UUID.fromString(incoming.trim()).toString();
        } catch (IllegalArgumentException notAUuid) {
            // The contract types the header as uuid; never echo arbitrary input back.
            return UUID.randomUUID().toString();
        }
    }
}
