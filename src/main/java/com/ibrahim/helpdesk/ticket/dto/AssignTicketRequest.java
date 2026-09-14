package com.ibrahim.helpdesk.ticket.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request to assign a ticket to a support agent. The administrator performing
 * the assignment is the authenticated user.
 */
public record AssignTicketRequest(

        @NotNull(message = "Agent id is required")
        Long agentId
) {
}
