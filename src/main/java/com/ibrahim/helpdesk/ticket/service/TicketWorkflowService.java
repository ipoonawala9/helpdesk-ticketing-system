package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.config.TicketWorkflowProperties;
import com.ibrahim.helpdesk.ticket.dto.AgentActionRequest;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CloseTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.ReopenTicketRequest;
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

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Controlled ticket state transitions. Every status change goes through a named
 * business action here; there is deliberately no endpoint that sets a status
 * directly.
 *
 * <pre>
 * assign   OPEN, ASSIGNED,
 *          IN_PROGRESS, REOPENED     -> ASSIGNED     org admin
 * start    ASSIGNED, REOPENED        -> IN_PROGRESS  assigned agent
 * resolve  IN_PROGRESS               -> RESOLVED     assigned agent
 * close    RESOLVED                  -> CLOSED       customer, or org admin after a grace period
 * reopen   RESOLVED, CLOSED          -> REOPENED     customer, CLOSED only within the reopen window
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class TicketWorkflowService {

    /**
     * OPEN is a first assignment. ASSIGNED, IN_PROGRESS and REOPENED are
     * reassignments, so a ticket is never stranded with an agent who has left
     * or been deactivated. Resolved and closed tickets cannot be assigned.
     */
    private static final Set<TicketStatus> ASSIGNABLE_STATUSES = EnumSet.of(
            TicketStatus.OPEN, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.REOPENED);

    /** A reopened ticket stays with its agent, who can pick it straight back up. */
    private static final Set<TicketStatus> STARTABLE_STATUSES =
            EnumSet.of(TicketStatus.ASSIGNED, TicketStatus.REOPENED);

    private static final Set<TicketStatus> REOPENABLE_STATUSES =
            EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final TicketService ticketService;
    private final UserService userService;
    private final TicketRepository ticketRepository;
    private final Clock clock;
    private final TicketWorkflowProperties properties;

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
        // retries do not bump updatedAt or send in-progress work back a step.
        if (ticket.getAssignedAgent() != null
                && Objects.equals(ticket.getAssignedAgent().getId(), agent.getId())) {
            return TicketMapper.toResponse(ticket);
        }

        // A new agent always starts from ASSIGNED, even if the previous agent
        // had already begun work.
        ticket.setAssignedAgent(agent);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setUpdatedAt(now());

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    /**
     * The assigned agent begins, or resumes, working on the ticket:
     * ASSIGNED or REOPENED -> IN_PROGRESS.
     */
    @Transactional
    public TicketResponse startWork(Long ticketId, AgentActionRequest request) {

        Ticket ticket = ticketService.findOrThrow(ticketId);
        requireAssignedAgent(ticket, userService.findOrThrow(request.agentId()), "start work on");
        requireStatus(ticket, STARTABLE_STATUSES, "start work on");

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setUpdatedAt(now());

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

        LocalDateTime now = now();
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(now);
        ticket.setUpdatedAt(now);

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    /**
     * The customer reports that the issue is not actually solved:
     * RESOLVED or CLOSED (within the reopen window) -> REOPENED.
     *
     * <p>The assigned agent is kept so they can resume straight away; an
     * administrator can still reassign the reopened ticket. resolvedAt and
     * closedAt are cleared because the ticket is now neither.
     */
    @Transactional
    public TicketResponse reopenTicket(Long ticketId, ReopenTicketRequest request) {

        Ticket ticket = ticketService.findOrThrow(ticketId);
        User actor = userService.findOrThrow(request.customerId());

        if (!isTicketCustomer(ticket, actor)) {
            throw new ForbiddenOperationException("Only the customer who opened this ticket can reopen it");
        }
        requireActive(actor, "reopen");
        requireStatus(ticket, REOPENABLE_STATUSES, "reopen");

        LocalDateTime now = now();
        if (ticket.getStatus() == TicketStatus.CLOSED && ticket.getClosedAt() != null
                && now.isAfter(ticket.getClosedAt().plus(properties.reopenWindow()))) {
            throw new InvalidTicketStateException(
                    "Tickets can only be reopened within " + describe(properties.reopenWindow())
                            + " of being closed; please open a new ticket");
        }

        int previousReopens = ticket.getReopenCount() == null ? 0 : ticket.getReopenCount();
        ticket.setStatus(TicketStatus.REOPENED);
        ticket.setReopenCount(previousReopens + 1);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        ticket.setUpdatedAt(now);

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    /**
     * Closes a resolved ticket: RESOLVED -> CLOSED.
     *
     * <p>The customer can confirm the resolution at any time. An administrator
     * of the ticket's organization can close it on the customer's behalf, but
     * only once the customer has had the configured time to respond.
     */
    @Transactional
    public TicketResponse closeTicket(Long ticketId, CloseTicketRequest request) {

        Ticket ticket = ticketService.findOrThrow(ticketId);
        User actor = userService.findOrThrow(request.userId());

        boolean isCustomer = isTicketCustomer(ticket, actor);
        boolean isAdmin = actor.getRole() == UserRole.ORG_ADMIN
                && belongsTo(actor, ticket.getOrganization());

        if (!isCustomer && !isAdmin) {
            throw new ForbiddenOperationException(
                    "Only the ticket's customer or an administrator of its organization can close this ticket");
        }
        requireActive(actor, "close");
        requireStatus(ticket, EnumSet.of(TicketStatus.RESOLVED), "close");

        LocalDateTime now = now();
        if (!isCustomer && ticket.getResolvedAt() != null) {
            LocalDateTime adminMayCloseFrom = ticket.getResolvedAt().plus(properties.adminCloseAfter());
            if (now.isBefore(adminMayCloseFrom)) {
                throw new InvalidTicketStateException(
                        "The customer has " + describe(properties.adminCloseAfter())
                                + " after resolution to close this ticket; an administrator can close it from "
                                + adminMayCloseFrom.truncatedTo(ChronoUnit.SECONDS));
            }
        }

        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(now);
        ticket.setUpdatedAt(now);

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static void requireStatus(Ticket ticket, Set<TicketStatus> allowed, String action) {
        if (!allowed.contains(ticket.getStatus())) {
            throw new InvalidTicketStateException(action, ticket.getStatus());
        }
    }

    private static void requireActive(User actor, String action) {
        if (!Boolean.TRUE.equals(actor.getActive())) {
            throw new ForbiddenOperationException("Inactive users cannot " + action + " tickets");
        }
    }

    private static boolean isTicketCustomer(Ticket ticket, User actor) {
        return actor.getRole() == UserRole.CUSTOMER
                && ticket.getCustomer() != null
                && Objects.equals(ticket.getCustomer().getId(), actor.getId());
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
        requireActive(actor, action);
    }

    private static void requireOrgAdminOf(User admin, Organization organization) {
        if (admin.getRole() != UserRole.ORG_ADMIN) {
            throw new ForbiddenOperationException("Only organization administrators can assign tickets");
        }
        requireActive(admin, "assign");
        if (!belongsTo(admin, organization)) {
            throw new ForbiddenOperationException(
                    "Administrators can only assign tickets from their own organization");
        }
    }

    private static void requireAssignableAgent(User agent, Organization organization) {
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

    /** Renders a window as "3 hours", "7 days" or "90 minutes" for error messages. */
    static String describe(Duration duration) {
        long days = duration.toDays();
        if (days > 0 && duration.equals(Duration.ofDays(days))) {
            return plural(days, "day");
        }
        long hours = duration.toHours();
        if (hours > 0 && duration.equals(Duration.ofHours(hours))) {
            return plural(hours, "hour");
        }
        return plural(duration.toMinutes(), "minute");
    }

    private static String plural(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }
}
