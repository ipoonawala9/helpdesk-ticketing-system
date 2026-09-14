package com.ibrahim.helpdesk.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new message on a ticket. The sender is the authenticated user. */
public record PostMessageRequest(

        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must be at most 5000 characters")
        String content
) {
}
