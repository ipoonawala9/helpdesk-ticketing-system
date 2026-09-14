package com.ibrahim.helpdesk.ticket.dto;

import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;

import java.util.Set;

/**
 * Optional filters for a ticket list. Every filter narrows the caller's
 * visible tickets further; none can widen them.
 *
 * @param statuses        any of these statuses; empty means all
 * @param priorities      any of these priorities; empty means all
 * @param categories      any of these categories; empty means all
 * @param customerId      tickets of this customer
 * @param assignedAgentId tickets assigned to this agent
 * @param unassigned      when true, only tickets with no agent
 * @param organizationId  tickets of this organization
 * @param query           case-insensitive text found in the ticket number, title or description
 */
public record TicketSearchCriteria(
        Set<TicketStatus> statuses,
        Set<TicketPriority> priorities,
        Set<TicketCategory> categories,
        Long customerId,
        Long assignedAgentId,
        Boolean unassigned,
        Long organizationId,
        String query
) {

    public static TicketSearchCriteria none() {
        return new TicketSearchCriteria(Set.of(), Set.of(), Set.of(), null, null, null, null, null);
    }
}
