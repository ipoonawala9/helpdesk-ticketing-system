package com.ibrahim.helpdesk.security.reset;

import com.ibrahim.helpdesk.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the reset link to the server log. Used when no mail server is
 * configured, so the feature works in development and in a deployment that has
 * no email yet: an administrator reads the link from the logs and passes it on.
 */
public class LoggingPasswordResetLinkSender implements PasswordResetLinkSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetLinkSender.class);

    @Override
    public void send(User user, String link) {
        log.warn("No mail server is configured, so this password reset link for {} is only written here. "
                + "Configure spring.mail.* to email it instead. Link: {}", user.getEmail(), link);
    }
}
