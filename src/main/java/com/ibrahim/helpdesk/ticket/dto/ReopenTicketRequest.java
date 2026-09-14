package com.ibrahim.helpdesk.ticket.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request to reopen a resolved or recently closed ticket.
 *
 * <p>{@code customerId} identifies the acting customer only because there is no
 * authentication yet. The rules are enforced against that user, but the
 * caller's claim to be that user is not verified.
 */
public record ReopenTicketRequest(

        @NotNull(message = "Customer id is required")
        Long customerId
) {
}
