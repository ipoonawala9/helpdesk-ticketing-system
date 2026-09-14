package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.common.paging.PageResponse;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketSearchCriteria;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.mapper.TicketMapper;
import com.ibrahim.helpdesk.ticket.priority.PriorityInput;
import com.ibrahim.helpdesk.ticket.priority.TicketPriorityPolicy;
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import com.ibrahim.helpdesk.ticket.repository.TicketSpecifications;
import org.springframework.data.domain.Pageable;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserService userService;
    private final TicketPriorityPolicy priorityPolicy;

    /**
     * Opens a ticket for the authenticated customer. Organization is derived
     * from the customer and never read from the request; status, priority,
     * agent, reopen count and timestamps are all set here.
     */
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, Long customerId) {

        User customer = userService.findOrThrow(customerId);
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new ForbiddenOperationException("Only customers can open tickets");
        }
        Organization organization = customer.getOrganization();

        Ticket ticket = new Ticket();

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());

        ticket.setCustomer(customer);
        ticket.setOrganization(organization);

        ticket.setStatus(TicketStatus.OPEN);
        ticket.setAssignedAgent(null);
        ticket.setReopenCount(0);
        ticket.setPriority(priorityPolicy.determine(priorityInputFor(ticket)));

        LocalDateTime now = LocalDateTime.now();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);

        Ticket savedTicket = ticketRepository.save(ticket);

        // The public ticket number embeds the database id, so it can only be
        // produced once the row exists.
        savedTicket.setTicketNumber(String.format("HD-2026-%06d", savedTicket.getId()));

        return TicketMapper.toResponse(ticketRepository.save(savedTicket));
    }

    /** API sort names and the ticket property each one sorts on. */
    public static final java.util.Map<String, String> SORTABLE_FIELDS = java.util.Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "priority", "priorityRank",
            "status", "statusRank",
            "ticketNumber", "ticketNumber",
            "title", "title");

    /**
     * A page of the tickets the viewer is allowed to see, narrowed by the
     * given filters: a customer's own tickets, an agent's assigned tickets, an
     * administrator's organization's tickets, or every ticket for a
     * SUPER_ADMIN. Filters can only narrow that scope.
     *
     * <p>Naming an organization other than the viewer's own is answered as if
     * it did not exist, consistent with every other organization lookup.
     */
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> listTickets(Long viewerId, TicketSearchCriteria criteria, Pageable pageable) {
        User viewer = userService.findOrThrow(viewerId);

        if (Boolean.TRUE.equals(criteria.unassigned()) && criteria.assignedAgentId() != null) {
            throw new BusinessRuleException("assignedAgentId cannot be combined with unassigned=true");
        }
        if (criteria.organizationId() != null && viewer.getRole() != UserRole.SUPER_ADMIN
                && (viewer.getOrganization() == null
                    || !criteria.organizationId().equals(viewer.getOrganization().getId()))) {
            throw new OrganizationNotFoundException(criteria.organizationId());
        }
        TicketSearchCriteria validated = new TicketSearchCriteria(
                criteria.statuses(), criteria.priorities(), criteria.categories(),
                criteria.customerId(), criteria.assignedAgentId(), criteria.unassigned(),
                criteria.organizationId(), PageRequests.searchTerm(criteria.query()));

        var tickets = ticketRepository.findAll(
                TicketSpecifications.visibleTo(viewer).and(TicketSpecifications.matching(validated)),
                pageable);

        return PageResponse.of(tickets, TicketMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id, Long viewerId) {
        return TicketMapper.toResponse(findVisibleOrThrow(id, userService.findOrThrow(viewerId)));
    }

    /**
     * Applies the only edits a customer is permitted to make. Priority is then
     * recalculated from the new content, because it is derived from exactly
     * these fields. Status, assignment, organization and every timestamp except
     * updatedAt are untouched by design.
     */
    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request, Long editorId) {

        User editor = userService.findOrThrow(editorId);
        Ticket ticket = findVisibleOrThrow(id, editor);
        if (!TicketParticipants.isCustomer(ticket, editor)) {
            throw new ForbiddenOperationException("Only the customer who opened this ticket can edit it");
        }

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(priorityPolicy.determine(priorityInputFor(ticket)));
        ticket.setUpdatedAt(LocalDateTime.now());

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    /** Only an administrator of the ticket's organization may delete it. */
    @Transactional
    public void deleteTicket(Long id, Long deleterId) {
        User deleter = userService.findOrThrow(deleterId);
        Ticket ticket = findVisibleOrThrow(id, deleter);
        if (!TicketParticipants.isOrgAdmin(ticket, deleter)) {
            throw new ForbiddenOperationException(
                    "Only an administrator of this ticket's organization can delete it");
        }
        ticketRepository.delete(ticket);
    }

    /**
     * Calculates priority for tickets that have none, which are tickets created
     * before priority was automatic. Tickets that already have a priority are
     * left alone, so running this repeatedly changes nothing.
     *
     * @return how many tickets were given a priority
     */
    @Transactional
    public int backfillMissingPriorities() {
        List<Ticket> tickets = ticketRepository.findByPriorityIsNull();
        tickets.forEach(ticket -> ticket.setPriority(priorityPolicy.determine(priorityInputFor(ticket))));
        ticketRepository.saveAll(tickets);
        return tickets.size();
    }

    /** The inputs the priority policy sees for a ticket in its current state. */
    public static PriorityInput priorityInputFor(Ticket ticket) {
        return new PriorityInput(
                ticket.getCategory(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getReopenCount() == null ? 0 : ticket.getReopenCount());
    }

    /**
     * Loads a ticket only if it is within the viewer's scope: their own ticket
     * for a customer, a ticket assigned to them for an agent, a ticket of their
     * organization for an administrator, any ticket for a SUPER_ADMIN.
     *
     * <p>The scope is part of the database query, and a ticket outside it is
     * reported exactly like one that does not exist, so ids from other
     * customers or organizations reveal nothing. Every user-facing ticket
     * operation loads its ticket through here; action-specific rules are
     * checked afterwards.
     */
    @Transactional(readOnly = true)
    public Ticket findVisibleOrThrow(Long ticketId, User viewer) {
        var ticket = switch (viewer.getRole()) {
            case SUPER_ADMIN -> ticketRepository.findById(ticketId);
            case ORG_ADMIN -> viewer.getOrganization() == null
                    ? java.util.Optional.<Ticket>empty()
                    : ticketRepository.findByIdAndOrganizationId(ticketId, viewer.getOrganization().getId());
            case SUPPORT_AGENT -> ticketRepository.findByIdAndAssignedAgentId(ticketId, viewer.getId());
            case CUSTOMER -> ticketRepository.findByIdAndCustomerId(ticketId, viewer.getId());
        };
        return ticket.orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    /** Unscoped lookup for internal use only; never for a request made by a user. */
    @Transactional(readOnly = true)
    public Ticket findOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }
}
