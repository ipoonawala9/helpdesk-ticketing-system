package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.mapper.TicketMapper;
import com.ibrahim.helpdesk.ticket.priority.PriorityInput;
import com.ibrahim.helpdesk.ticket.priority.TicketPriorityPolicy;
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import com.ibrahim.helpdesk.user.entity.User;
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
     * Opens a ticket on behalf of a customer. Organization is derived from the
     * customer and never read from the request; status, priority, agent, reopen
     * count and timestamps are all set here.
     */
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {

        User customer = userService.findOrThrow(request.customerId());
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

    @Transactional(readOnly = true)
    public List<TicketResponse> getAllTickets() {
        return ticketRepository.findAll()
                .stream()
                .map(TicketMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        return TicketMapper.toResponse(findOrThrow(id));
    }

    /**
     * Applies the only edits a customer is permitted to make. Priority is then
     * recalculated from the new content, because it is derived from exactly
     * these fields. Status, assignment, organization and every timestamp except
     * updatedAt are untouched by design.
     */
    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {

        Ticket ticket = findOrThrow(id);

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(priorityPolicy.determine(priorityInputFor(ticket)));
        ticket.setUpdatedAt(LocalDateTime.now());

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public void deleteTicket(Long id) {
        ticketRepository.delete(findOrThrow(id));
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

    @Transactional(readOnly = true)
    public Ticket findOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }
}
