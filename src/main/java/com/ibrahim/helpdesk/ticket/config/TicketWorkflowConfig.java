package com.ibrahim.helpdesk.ticket.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(TicketWorkflowProperties.class)
public class TicketWorkflowConfig {

    /**
     * Time source for the workflow's time-based rules, injected rather than
     * read statically so tests can control it.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
