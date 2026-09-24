package com.ibrahim.helpdesk.security.reset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties({PasswordResetProperties.class, FrontendProperties.class})
public class PasswordResetConfig {

    /** Email delivery as soon as a mail server is configured. */
    @Bean
    @ConditionalOnProperty("spring.mail.host")
    public PasswordResetLinkSender emailPasswordResetLinkSender(
            JavaMailSender mailSender, PasswordResetProperties properties) {
        return new EmailPasswordResetLinkSender(mailSender, properties);
    }

    /** Otherwise the link goes to the log, so the feature still works. */
    @Bean
    @ConditionalOnMissingBean(PasswordResetLinkSender.class)
    public PasswordResetLinkSender loggingPasswordResetLinkSender() {
        return new LoggingPasswordResetLinkSender();
    }
}
