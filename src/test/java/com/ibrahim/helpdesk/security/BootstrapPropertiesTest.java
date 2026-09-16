package com.ibrahim.helpdesk.security;

import com.ibrahim.helpdesk.security.bootstrap.BootstrapProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapPropertiesTest {

    @Test
    @DisplayName("a configured super admin password shorter than 12 characters stops startup")
    void rejectsShortPassword() {
        assertThatThrownBy(() -> new BootstrapProperties("root@example.test", "short-pass1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BOOTSTRAP_SUPER_ADMIN_PASSWORD must be at least 12 characters");
    }

    @Test
    @DisplayName("12 characters is enough, and leaving the bootstrap unset is allowed")
    void acceptsLongPasswordOrUnset() {
        assertThatNoException().isThrownBy(() -> new BootstrapProperties("root@example.test", "twelve-chars", null));
        assertThat(new BootstrapProperties("", "", null).isConfigured()).isFalse();
        assertThat(new BootstrapProperties(null, null, null).isConfigured()).isFalse();
    }
}
