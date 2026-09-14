package com.ibrahim.helpdesk.ticket.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketWorkflowPropertiesTest {

    private static TicketWorkflowProperties bind(Iterable<ConfigurationPropertySource> sources) {
        return new Binder(sources)
                .bindOrCreate("helpdesk.tickets", TicketWorkflowProperties.class);
    }

    @Test
    @DisplayName("defaults to a 3 hour admin close delay and a 7 day reopen window")
    void defaults() {
        TicketWorkflowProperties properties = bind(List.of(new MapConfigurationPropertySource()));

        assertThat(properties.adminCloseAfter()).isEqualTo(Duration.ofHours(3));
        assertThat(properties.reopenWindow()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("binds overrides from environment variables named the way the README documents")
    void bindsFromEnvironmentVariables() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("HELPDESK_TICKETS_ADMIN_CLOSE_AFTER", "PT2H",
                        "HELPDESK_TICKETS_REOPEN_WINDOW", "P14D")));

        TicketWorkflowProperties properties = bind(ConfigurationPropertySources.from(environment.getPropertySources()));

        assertThat(properties.adminCloseAfter()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.reopenWindow()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("rejects a negative window")
    void rejectsNegativeWindow() {
        assertThatThrownBy(() -> new TicketWorkflowProperties(Duration.ofHours(-1), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admin-close-after");
    }
}
