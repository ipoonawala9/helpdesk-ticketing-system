package com.ibrahim.helpdesk.ticket.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request to assign a ticket to a support agent.
 *
 * <p>{@code adminId} identifies the organization administrator performing the
 * assignment. It exists only because there is no authentication yet: every
 * role and organization rule is enforced against it, but the caller's claim to
 * be that admin is not verified. It will be removed once the acting user is
 * taken from the security context.
 */
public record AssignTicketRequest(

        @NotNull(message = "Agent id is required")
        Long agentId,

        @NotNull(message = "Admin id is required")
        Long adminId
) {
}
