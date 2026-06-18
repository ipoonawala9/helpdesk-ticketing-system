package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public Ticket createTicket(CreateTicketRequest request) {

        User customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() ->
                        new UserNotFoundException(request.getCustomerId()));

        Organization organization = customer.getOrganization();

        Ticket ticket = new Ticket();

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());

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

        String ticketNumber = String.format(
                "HD-2026-%06d",
                savedTicket.getId()
        );

        savedTicket.setTicketNumber(ticketNumber);

        return ticketRepository.save(savedTicket);
    }
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }
    public Ticket getTicketById(Long id) {

        return ticketRepository.findById(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(id));
    }
    public void deleteTicket(Long id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(id));

        ticketRepository.delete(ticket);
    }
    public Ticket updateTicket(
            Long id,
            UpdateTicketRequest request) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(id));

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());

        ticket.setUpdatedAt(LocalDateTime.now());

        return ticketRepository.save(ticket);
    }
}