package io.github.dmitrykislov.contractfirst.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemFactoryTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/orders");

    @Test
    void worksWithOpaqueUrnNamespacesAsWellAsHttpOnes() {
        assertThat(new ProblemFactory("urn:problem-type:").typeFor("Not Found")).hasToString("urn:problem-type:not-found");
        assertThat(new ProblemFactory("https://d.test/p/").typeFor("  Validation  failed! ")).hasToString("https://d.test/p/validation-failed");
    }

    @Test
    void buildsAbsoluteInstanceAndOnlyAddsErrorsWhenPresent() {
        ProblemFactory factory = new ProblemFactory("urn:problem-type:");
        ProblemDetail plain = factory.of(HttpStatus.CONFLICT, "Conflict", "already there", request);
        assertThat(plain.getInstance()).hasToString("http://localhost/v1/orders");
        assertThat(plain.getProperties()).isNull();

        ProblemDetail withErrors = factory.of(HttpStatus.BAD_REQUEST, "Validation failed", null, request,
                List.of(factory.fieldError("qty", "must be >= 1", 0), factory.fieldError("blob", "too long", "x".repeat(500))));
        assertThat(withErrors.getProperties()).containsKey("errors");
        @SuppressWarnings("unchecked")
        List<ProblemFieldError> errors = (List<ProblemFieldError>) withErrors.getProperties().get("errors");
        assertThat(errors).containsExactly(new ProblemFieldError("qty", "must be >= 1", "0"), new ProblemFieldError("blob", "too long", null));
    }

    @Test
    void decorateFillsOnlyWhatIsMissing() {
        ProblemFactory factory = new ProblemFactory("urn:problem-type:");
        ProblemDetail springMade = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Required header 'X' is not present.");
        ProblemDetail decorated = factory.decorate(springMade, request);
        assertThat(decorated.getTitle()).isEqualTo("Bad Request");
        assertThat(decorated.getType()).hasToString("urn:problem-type:bad-request");
        assertThat(decorated.getDetail()).isEqualTo("Required header 'X' is not present.");

        ProblemDetail custom = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        custom.setType(java.net.URI.create("https://elsewhere/forbidden"));
        assertThat(factory.decorate(custom, request).getType()).hasToString("https://elsewhere/forbidden");
    }
}
