package com.ibrahim.helpdesk.ticket.controller;

import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public Ticket createTicket(
            @RequestBody CreateTicketRequest request) {

        return ticketService.createTicket(request);
    }
    @GetMapping
    public List<Ticket> getAllTickets() {
        return ticketService.getAllTickets();
    }
    @GetMapping("/{id}")
    public Ticket getTicketById(
            @PathVariable Long id) {

        return ticketService.getTicketById(id);
    }
    @DeleteMapping("/{id}")
    public void deleteTicket(
            @PathVariable Long id) {

        ticketService.deleteTicket(id);
    }
    @PutMapping("/{id}")
    public Ticket updateTicket(
            @PathVariable Long id,
            @RequestBody UpdateTicketRequest request) {

        return ticketService.updateTicket(id, request);
    }
}