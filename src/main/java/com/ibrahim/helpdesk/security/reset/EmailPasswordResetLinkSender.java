package com.ibrahim.helpdesk.security.reset;

import com.ibrahim.helpdesk.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

/** Emails the reset link. Active when a mail server is configured. */
@RequiredArgsConstructor
public class EmailPasswordResetLinkSender implements PasswordResetLinkSender {

    private static final Logger log = LoggerFactory.getLogger(EmailPasswordResetLinkSender.class);

    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    @Override
    public void send(User user, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(user.getEmail());
        message.setSubject("Reset your HelpDesk password");
        message.setText("""
                Hello %s,

                Use this link to choose a new HelpDesk password:

                %s

                The link works once and expires in %d minutes. If you did not ask for it,
                you can ignore this message; your password stays as it is.
                """.formatted(user.getName(), link, Duration.ofMillis(properties.validFor().toMillis()).toMinutes()));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // The caller must not learn whether the address exists, so a failure
            // here is logged rather than returned.
            log.error("Could not send a password reset email to {}", user.getEmail(), e);
        }
    }
}
