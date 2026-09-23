package com.ibrahim.helpdesk.security.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first super admin, created on startup if no account with this email
 * exists. Bound from {@code helpdesk.bootstrap.super-admin.*}; leave the
 * email or password empty to skip.
 */
@ConfigurationProperties(prefix = "helpdesk.bootstrap.super-admin")
public record BootstrapProperties(String email, String password, String name, boolean resetPassword) {

    /** The first super admin controls every organization, so its password must be substantial. */
    static final int MIN_PASSWORD_LENGTH = 12;

    public BootstrapProperties {
        if (email != null && !email.isBlank() && password != null && !password.isBlank()
                && password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_PASSWORD must be at least "
                    + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
