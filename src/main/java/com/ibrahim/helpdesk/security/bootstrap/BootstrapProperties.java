package com.ibrahim.helpdesk.security.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first super admin, created on startup if no account with this email
 * exists. Bound from {@code helpdesk.bootstrap.super-admin.*}; leave the
 * email or password empty to skip.
 */
@ConfigurationProperties(prefix = "helpdesk.bootstrap.super-admin")
public record BootstrapProperties(String email, String password, String name) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
