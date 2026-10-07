package io.github.dmitrykislov.contractfirst.testing.restclient;

import io.github.dmitrykislov.contractfirst.testing.ContractCoverage;
import io.github.dmitrykislov.contractfirst.testing.Contract;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Validates each outgoing request and incoming response of a {@code RestClient} against a contract and
 * throws {@link ContractViolationException} on any error, so a non-conformant exchange fails the test at
 * the exact call that produced it. Records every exchange for {@link ContractCoverage}.
 *
 * <p>Built on {@code openapi-request-validator-core} rather than the library's own Spring interceptor,
 * which hands percent-encoded query values (e.g. {@code 00%3A00%3A00Z}) to the schema validator and
 * therefore rejects every correctly encoded {@code date-time} parameter.
 */
public final class ContractValidatingInterceptor implements ClientHttpRequestInterceptor {

    private final Contract contract;

    public ContractValidatingInterceptor(Contract contract) {
        this.contract = contract;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Request contractRequest = toRequest(request, body);
        ClientHttpResponse response = new BufferedResponse(execution.execute(request, body));
        ValidationReport report = contract.validator().validate(contractRequest, toResponse(response));
        contract.coverage().record(request.getMethod().name(), request.getURI().getPath(), response.getStatusCode().value());
        if (report.hasErrors()) {
            throw new ContractViolationException(request, response, report);
        }
        return response;
    }

    private static Request toRequest(HttpRequest request, byte[] body) {
        UriComponents uri = UriComponentsBuilder.fromUri(request.getURI()).build();
        SimpleRequest.Builder builder = new SimpleRequest.Builder(request.getMethod().name(), uri.getPath());
        request.getHeaders().forEach(builder::withHeader);
        // Decode so the validator sees the values the server will see, not their wire encoding.
        uri.getQueryParams().forEach((name, values) -> builder.withQueryParam(
                name, values.stream().map(v -> URLDecoder.decode(v, StandardCharsets.UTF_8)).toList()));
        if (body.length > 0) {
            builder.withBody(body);
        }
        return builder.build();
    }

    private static Response toResponse(ClientHttpResponse response) throws IOException {
        SimpleResponse.Builder builder = new SimpleResponse.Builder(response.getStatusCode().value());
        response.getHeaders().forEach(builder::withHeader);
        byte[] body = StreamUtils.copyToByteArray(response.getBody());
        if (body.length > 0) {
            builder.withBody(body);
        }
        return builder.build();
    }

    /** Reads the body once so both the validator and the caller can consume it. */
    private static final class BufferedResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferedResponse(ClientHttpResponse delegate) throws IOException {
            this.delegate = delegate;
            this.body = StreamUtils.copyToByteArray(delegate.getBody());
        }

        @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
        @Override public void close() { delegate.close(); }
    }

    public static final class ContractViolationException extends RuntimeException {
        private final transient ValidationReport report;

        ContractViolationException(HttpRequest request, ClientHttpResponse response, ValidationReport report)
                throws IOException {
            super("%s %s -> %d violates the contract:%n%s".formatted(request.getMethod(), request.getURI(),
                    response.getStatusCode().value(), SimpleValidationReportFormat.getInstance().apply(report)));
            this.report = report;
        }

        public ValidationReport report() {
            return report;
        }
    }
}
