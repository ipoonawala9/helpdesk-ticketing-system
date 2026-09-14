package com.ibrahim.helpdesk.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    @ParameterizedTest(name = "secret \"{0}\" is refused")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("the application will not start without a signing secret")
    void missingSecret(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, Duration.ofHours(1), "helpdesk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("a secret shorter than 32 bytes is refused")
    void shortSecret() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET.substring(1), Duration.ofHours(1), "helpdesk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("a zero or negative token lifetime is refused")
    void nonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, Duration.ZERO, "helpdesk"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, Duration.ofMinutes(-5), "helpdesk"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsA32ByteSecret() {
        assertThat(new JwtProperties(VALID_SECRET, Duration.ofHours(1), "helpdesk").secretBytes()).hasSize(32);
    }
}
