package com.ibrahim.helpdesk.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunable time windows for the ticket workflow, bound from
 * {@code helpdesk.tickets.*}.
 *
 * @param adminCloseAfter how long after resolution the customer has to confirm
 *                        before an organization administrator may close the
 *                        ticket instead
 * @param reopenWindow    how long after closure the customer may still reopen
 *                        the ticket
 */
@ConfigurationProperties(prefix = "helpdesk.tickets")
public record TicketWorkflowProperties(
        @DefaultValue("PT3H") Duration adminCloseAfter,
        @DefaultValue("P7D") Duration reopenWindow
) {

    public TicketWorkflowProperties {
        if (adminCloseAfter.isNegative()) {
            throw new IllegalArgumentException("helpdesk.tickets.admin-close-after must not be negative");
        }
        if (reopenWindow.isNegative()) {
            throw new IllegalArgumentException("helpdesk.tickets.reopen-window must not be negative");
        }
    }
}
