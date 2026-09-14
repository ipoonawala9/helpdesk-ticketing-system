package com.ibrahim.helpdesk.ticket.controller;

import com.ibrahim.helpdesk.ticket.dto.AgentActionRequest;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CloseTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.ReopenTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.ticket.service.TicketWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketWorkflowService ticketWorkflowService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(request);
    }

    @GetMapping
    public List<TicketResponse> getAllTickets() {
        return ticketService.getAllTickets();
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id) {
        return ticketService.getTicketById(id);
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request) {

        return ticketService.updateTicket(id, request);
    }

    @PostMapping("/{id}/assign")
    public TicketResponse assignTicket(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request) {

        return ticketWorkflowService.assignTicket(id, request);
    }

    @PostMapping("/{id}/start")
    public TicketResponse startWork(
            @PathVariable Long id,
            @Valid @RequestBody AgentActionRequest request) {

        return ticketWorkflowService.startWork(id, request);
    }

    @PostMapping("/{id}/resolve")
    public TicketResponse resolveTicket(
            @PathVariable Long id,
            @Valid @RequestBody AgentActionRequest request) {

        return ticketWorkflowService.resolveTicket(id, request);
    }

    @PostMapping("/{id}/reopen")
    public TicketResponse reopenTicket(
            @PathVariable Long id,
            @Valid @RequestBody ReopenTicketRequest request) {

        return ticketWorkflowService.reopenTicket(id, request);
    }

    @PostMapping("/{id}/close")
    public TicketResponse closeTicket(
            @PathVariable Long id,
            @Valid @RequestBody CloseTicketRequest request) {

        return ticketWorkflowService.closeTicket(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
    }
}
