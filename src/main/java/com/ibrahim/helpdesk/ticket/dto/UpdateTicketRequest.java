package com.ibrahim.helpdesk.ticket.dto;

import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The only ticket fields a customer may edit after creation.
 */
public record UpdateTicketRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "Category is required")
        TicketCategory category
) {
}
