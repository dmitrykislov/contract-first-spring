package io.github.dmitrykislov.orders.server.security;

import io.github.dmitrykislov.orders.server.web.ProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders authentication failures as the contract's 401 problem (Spring's {@code ProblemDetail},
 * serialised by Boot's {@code JsonMapper} in RFC 9457 shape), so even rejected requests are
 * spec-conformant.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;
    private final ProblemFactory problems;

    public ProblemAuthenticationEntryPoint(JsonMapper jsonMapper, ProblemFactory problems) {
        this.jsonMapper = jsonMapper;
        this.problems = problems;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String detail = exception instanceof BadCredentialsException
                ? "Unrecognised API key"
                : "Missing %s header".formatted(ApiKeyAuthenticationConverter.HEADER);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(),
                problems.of(HttpStatus.UNAUTHORIZED, "Unauthorized", detail, request));
    }
}
