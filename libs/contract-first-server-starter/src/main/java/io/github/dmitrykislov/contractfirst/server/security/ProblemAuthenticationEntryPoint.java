package io.github.dmitrykislov.contractfirst.server.security;

import io.github.dmitrykislov.contractfirst.server.json.ContractJsonMapper;
import io.github.dmitrykislov.contractfirst.server.problem.ProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Renders authentication failures as a 401 {@code ProblemDetail}, so even rejected requests are spec-conformant. */
public final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ContractJsonMapper json;
    private final ProblemFactory problems;
    private final String headerName;

    public ProblemAuthenticationEntryPoint(ContractJsonMapper json, ProblemFactory problems, String headerName) {
        this.json = json;
        this.problems = problems;
        this.headerName = headerName;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String detail = exception instanceof BadCredentialsException
                ? "Unrecognised API key"
                : "Missing %s header".formatted(headerName);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.mapper().writeValue(response.getOutputStream(),
                problems.of(HttpStatus.UNAUTHORIZED, "Unauthorized", detail, request));
    }
}
