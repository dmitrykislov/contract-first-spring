package io.github.dmitrykislov.orders.testsupport;

import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Checks recorded MockMvc exchanges against the contract. Two flavours:
 * <ul>
 *   <li>{@link #assertExchangeConforms} validates request <em>and</em> response: use it on the happy
 *       path and on business errors (404, 409, ...) where the request itself is legal.</li>
 *   <li>{@link #assertResponseConforms} validates only the response: use it when the test
 *       deliberately sends an invalid request (missing API key, schema violation) and wants to prove
 *       the server still answers with a spec-conformant problem.</li>
 * </ul>
 */
public final class MockMvcContract {

    private MockMvcContract() {}

    public static void assertExchangeConforms(MvcTestResult result) {
        ValidationReport report = OrdersContract.validator()
                .validate(toRequest(result.getRequest()), toResponse(result.getResponse()));
        assertNoErrors(report, result);
    }

    public static void assertResponseConforms(MvcTestResult result) {
        MockHttpServletRequest request = result.getRequest();
        ValidationReport report = OrdersContract.validator().validateResponse(
                request.getRequestURI(), Request.Method.valueOf(request.getMethod()), toResponse(result.getResponse()));
        assertNoErrors(report, result);
    }

    private static void assertNoErrors(ValidationReport report, MvcTestResult result) {
        if (report.hasErrors()) {
            Assertions.fail("%s %s -> %d does not conform to %s:%n%s".formatted(
                    result.getRequest().getMethod(), result.getRequest().getRequestURI(),
                    result.getResponse().getStatus(), OrdersContract.SPEC_RESOURCE,
                    SimpleValidationReportFormat.getInstance().apply(report)));
        }
    }

    private static Request toRequest(MockHttpServletRequest request) {
        SimpleRequest.Builder builder = new SimpleRequest.Builder(request.getMethod(), request.getRequestURI());
        Collections.list(request.getHeaderNames())
                .forEach(name -> builder.withHeader(name, Collections.list(request.getHeaders(name))));
        request.getParameterMap().forEach((name, values) -> builder.withQueryParam(name, List.of(values)));
        try {
            String body = request.getContentAsString();
            if (body != null && !body.isEmpty()) {
                builder.withBody(body);
            }
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
        return builder.build();
    }

    private static SimpleResponse toResponse(MockHttpServletResponse response) {
        SimpleResponse.Builder builder = new SimpleResponse.Builder(response.getStatus());
        response.getHeaderNames().forEach(name -> builder.withHeader(name, response.getHeaders(name)));
        try {
            String body = response.getContentAsString();
            if (!body.isEmpty()) {
                builder.withBody(body);
            }
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
        return builder.build();
    }
}
