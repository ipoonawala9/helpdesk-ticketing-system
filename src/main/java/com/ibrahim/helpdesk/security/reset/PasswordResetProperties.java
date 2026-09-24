package com.ibrahim.helpdesk.security.reset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for password reset links, bound from
 * {@code helpdesk.security.password-reset.*}.
 *
 * @param validFor    how long a link works before it expires
 * @param maxRequests reset requests allowed for one email within {@code validFor}
 * @param from        sender address used when email is configured
 */
@ConfigurationProperties(prefix = "helpdesk.security.password-reset")
public record PasswordResetProperties(
        @DefaultValue("PT30M") Duration validFor,
        @DefaultValue("3") int maxRequests,
        @DefaultValue("no-reply@helpdesk.local") String from
) {

    public PasswordResetProperties {
        if (validFor.isNegative() || validFor.isZero()) {
            throw new IllegalStateException("helpdesk.security.password-reset.valid-for must be positive");
        }
        if (maxRequests < 1) {
            throw new IllegalStateException("helpdesk.security.password-reset.max-requests must be at least 1");
        }
    }
}
