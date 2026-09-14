package com.ibrahim.helpdesk.ticket.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for actions performed by the ticket's assigned agent, such as
 * starting work or resolving the ticket.
 *
 * <p>{@code agentId} identifies the acting agent only because there is no
 * authentication yet. The workflow rules are enforced against that user, but
 * the caller's claim to be that user is not verified. It will be removed once
 * the acting user is taken from the security context.
 */
public record AgentActionRequest(

        @NotNull(message = "Agent id is required")
        Long agentId
) {
}
