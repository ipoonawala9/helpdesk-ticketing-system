package com.ibrahim.helpdesk.ticket.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request to close a resolved ticket, made either by the ticket's customer or
 * by an administrator of its organization.
 *
 * <p>{@code userId} identifies the acting user only because there is no
 * authentication yet. The rules are enforced against that user, but the
 * caller's claim to be that user is not verified.
 */
public record CloseTicketRequest(

        @NotNull(message = "User id is required")
        Long userId
) {
}
