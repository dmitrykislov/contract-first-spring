package io.github.dmitrykislov.contractfirst.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    void schemeIsInsertedWithOneSpaceAndStrippedCaseInsensitively() {
        AuthProperties auth = new AuthProperties(AuthProperties.Mode.PROPAGATE, "Authorization", " Bearer ", null);

        assertThat(auth.headerValue("abc")).isEqualTo("Bearer abc");
        assertThat(auth.rawToken("bearer abc")).isEqualTo("abc");
        assertThat(auth.rawToken("Bearer   abc ")).isEqualTo("abc");
        assertThat(auth.rawToken("abc")).isEqualTo("abc");
        assertThat(auth.rawToken("Bearerabc")).isEqualTo("Bearerabc");
    }

    @Test
    void withoutSchemeTheTokenIsSentVerbatim() {
        AuthProperties auth = new AuthProperties(AuthProperties.Mode.STATIC, "X-API-Key", "", "k");
        assertThat(auth.headerValue("k")).isEqualTo("k");
        assertThat(auth.rawToken(" k ")).isEqualTo("k");
    }

    @Test
    void staticModeRequiresATokenAndHeaderNameMustNotBeBlank() {
        assertThatThrownBy(() -> new AuthProperties(AuthProperties.Mode.STATIC, "X-API-Key", "", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("auth.token");
        assertThatThrownBy(() -> new AuthProperties(AuthProperties.Mode.PROPAGATE, "", "", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("header-name");
        assertThat(new AuthProperties(AuthProperties.Mode.PROVIDER, "X-API-Key", null, null).scheme()).isEmpty();
    }
}
