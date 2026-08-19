package com.ibrahim.helpdesk.ticket.mapper;

import com.ibrahim.helpdesk.organization.mapper.OrganizationMapper;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.user.mapper.UserMapper;

/**
 * Explicit entity -> DTO mapping for tickets. Must be called inside an active
 * transaction so the lazy customer, agent and organization associations can be
 * resolved before the response leaves the service layer.
 */
public final class TicketMapper {

    private TicketMapper() {
    }

    public static TicketResponse toResponse(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory(),
                UserMapper.toSummary(ticket.getCustomer()),
                UserMapper.toSummary(ticket.getAssignedAgent()),
                OrganizationMapper.toSummary(ticket.getOrganization()),
                ticket.getReopenCount(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt()
        );
    }
}
