package com.ibrahim.helpdesk.ticket.dto;

import com.ibrahim.helpdesk.organization.dto.OrganizationSummaryResponse;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.user.dto.UserSummaryResponse;

import java.time.LocalDateTime;

/**
 * Safe outward-facing view of a Ticket. Related entities are flattened into
 * summary records, so no lazy proxy is ever handed to Jackson and there is no
 * path back from a ticket to a password or to another ticket.
 */
public record TicketResponse(
        Long id,
        String ticketNumber,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        UserSummaryResponse customer,
        UserSummaryResponse assignedAgent,
        OrganizationSummaryResponse organization,
        Integer reopenCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt
) {
}
