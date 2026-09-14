package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.dto.AgentActionRequest;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.mapper.TicketMapper;
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Controlled ticket state transitions. Every status change goes through a named
 * business action here; there is deliberately no endpoint that sets a status
 * directly.
 */
@Service
@RequiredArgsConstructor
public class TicketWorkflowService {

    /**
     * OPEN is a first assignment; ASSIGNED is a reassignment before any work
     * has started. Reassignment of in-progress and reopened tickets is defined
     * with the reopen/close workflow.
     */
    private static final Set<TicketStatus> ASSIGNABLE_STATUSES =
            EnumSet.of(TicketStatus.OPEN, TicketStatus.ASSIGNED);

    private final TicketService ticketService;
    private final UserService userService;
    private final TicketRepository ticketRepository;

    @Transactional
    public TicketResponse assignTicket(Long ticketId, AssignTicketRequest request) {

        Ticket ticket = ticketService.findOrThrow(ticketId);

        // Authorise the actor before looking at the agent, so a caller without
        // permission learns nothing about other users.
        User admin = userService.findOrThrow(request.adminId());
        requireOrgAdminOf(admin, ticket.getOrganization());

        requireStatus(ticket, ASSIGNABLE_STATUSES, "assign");

        User agent = userService.findOrThrow(request.agentId());
        requireAssignableAgent(agent, ticket.getOrganization());

        // Re-assigning to the agent who already holds the ticket is a no-op, so
        // retries do not bump updatedAt.
        if (ticket.getAssignedAgent() != null
                && Objects.equals(ticket.getAssignedAgent().getId(), agent.getId())) {
            return TicketMapper.toResponse(ticket);
        }

        ticket.setAssignedAgent(agent);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setUpdatedAt(LocalDateTime.now());

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    /**
     * The assigned agent begins working on the ticket: ASSIGNED -> IN_PROGRESS.
     */
    @Transactional
    public TicketResponse startWork(Long ticketId, AgentActionRequest request) {

        Ticket ticket = ticketService.findOrThrow(ticketId);
        requireAssignedAgent(ticket, userService.findOrThrow(request.agentId()), "start work on");
        requireStatus(ticket, EnumSet.of(TicketStatus.ASSIGNED), "start work on");

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setUpdatedAt(LocalDateTime.now());

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    /**
     * The assigned agent marks the issue as fixed: IN_PROGRESS -> RESOLVED.
     * The ticket is not closed here; closure follows customer confirmation.
     */
    @Transactional
    public TicketResponse resolveTicket(Long ticketId, AgentActionRequest request) {

        Ticket ticket = ticketService.findOrThrow(ticketId);
        requireAssignedAgent(ticket, userService.findOrThrow(request.agentId()), "resolve");
        requireStatus(ticket, EnumSet.of(TicketStatus.IN_PROGRESS), "resolve");

        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(now);
        ticket.setUpdatedAt(now);

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    private static void requireStatus(Ticket ticket, Set<TicketStatus> allowed, String action) {
        if (!allowed.contains(ticket.getStatus())) {
            throw new InvalidTicketStateException(action, ticket.getStatus());
        }
    }

    /**
     * Only the agent currently holding the ticket may act on it. Role and
     * active flag are re-checked because either may have changed since the
     * ticket was assigned.
     */
    private static void requireAssignedAgent(Ticket ticket, User actor, String action) {
        boolean isAssignedAgent = ticket.getAssignedAgent() != null
                && Objects.equals(ticket.getAssignedAgent().getId(), actor.getId());

        if (!isAssignedAgent || actor.getRole() != UserRole.SUPPORT_AGENT) {
            throw new ForbiddenOperationException(
                    "Only the assigned agent can " + action + " this ticket");
        }
        if (!Boolean.TRUE.equals(actor.getActive())) {
            throw new ForbiddenOperationException("Inactive users cannot " + action + " tickets");
        }
    }

    private void requireOrgAdminOf(User admin, Organization organization) {
        if (admin.getRole() != UserRole.ORG_ADMIN) {
            throw new ForbiddenOperationException("Only organization administrators can assign tickets");
        }
        if (!Boolean.TRUE.equals(admin.getActive())) {
            throw new ForbiddenOperationException("Inactive users cannot assign tickets");
        }
        if (!belongsTo(admin, organization)) {
            throw new ForbiddenOperationException(
                    "Administrators can only assign tickets from their own organization");
        }
    }

    private void requireAssignableAgent(User agent, Organization organization) {
        if (agent.getRole() != UserRole.SUPPORT_AGENT) {
            throw new BusinessRuleException("Tickets can only be assigned to users with role SUPPORT_AGENT");
        }
        if (!Boolean.TRUE.equals(agent.getActive())) {
            throw new BusinessRuleException("Tickets cannot be assigned to an inactive agent");
        }
        if (!belongsTo(agent, organization)) {
            throw new BusinessRuleException("Agent must belong to the ticket's organization");
        }
    }

    private static boolean belongsTo(User user, Organization organization) {
        return user.getOrganization() != null
                && organization != null
                && Objects.equals(user.getOrganization().getId(), organization.getId());
    }
}
