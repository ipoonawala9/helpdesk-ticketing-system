package com.ibrahim.helpdesk.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new message on a ticket.
 *
 * <p>{@code senderId} identifies the author only because there is no
 * authentication yet. The access rules are enforced against that user, but the
 * caller's claim to be that user is not verified.
 */
public record PostMessageRequest(

        @NotNull(message = "Sender id is required")
        Long senderId,

        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must be at most 5000 characters")
        String content
) {
}
