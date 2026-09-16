package com.ibrahim.helpdesk.security.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits on password guessing, bound from {@code helpdesk.security.login.*}.
 *
 * @param maxFailures wrong passwords allowed for one email within {@code window}
 * @param window      the period failures are counted over, and how long sign-in
 *                    stays blocked once the limit is reached
 */
@ConfigurationProperties(prefix = "helpdesk.security.login")
public record LoginThrottleProperties(
        @DefaultValue("5") int maxFailures,
        @DefaultValue("PT15M") Duration window
) {

    public LoginThrottleProperties {
        if (maxFailures < 1) {
            throw new IllegalStateException("helpdesk.security.login.max-failures must be at least 1");
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalStateException("helpdesk.security.login.window must be positive");
        }
    }
}
