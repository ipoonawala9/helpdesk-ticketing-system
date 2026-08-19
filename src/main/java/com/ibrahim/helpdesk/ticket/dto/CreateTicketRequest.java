package com.ibrahim.helpdesk.ticket.dto;

import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Fields a customer is allowed to supply when opening a ticket. Status,
 * priority, assigned agent, organization, ticket number, reopen count and all
 * timestamps are server-controlled and are deliberately absent.
 */
public record CreateTicketRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "Category is required")
        TicketCategory category,

        @NotNull(message = "Customer id is required")
        Long customerId
) {
}
