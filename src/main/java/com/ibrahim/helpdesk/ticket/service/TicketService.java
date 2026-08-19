package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.mapper.TicketMapper;
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

    /**
     * Opens a ticket on behalf of a customer. Organization is derived from the
     * customer and never read from the request; status, agent, reopen count and
     * timestamps are all set here.
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
     * Applies the only edits a customer is permitted to make. Status, priority,
     * assignment, organization and every timestamp except updatedAt are
     * untouched by design.
     */
    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {

        Ticket ticket = findOrThrow(id);

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setUpdatedAt(LocalDateTime.now());

        return TicketMapper.toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public void deleteTicket(Long id) {
        ticketRepository.delete(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Ticket findOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }
}
