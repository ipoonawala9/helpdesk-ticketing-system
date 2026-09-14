package com.ibrahim.helpdesk.ticket.controller;

import com.ibrahim.helpdesk.security.auth.CurrentUserId;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.ticket.service.TicketWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code @PreAuthorize} states which roles may call each endpoint. Whether the
 * caller is the right user for this particular ticket, such as its customer or
 * its assigned agent, is checked in the services.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketWorkflowService ticketWorkflowService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @CurrentUserId Long currentUserId) {

        return ticketService.createTicket(request, currentUserId);
    }

    /** System-wide list; organization-scoped lists for other roles come with tenant isolation. */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<TicketResponse> getAllTickets() {
        return ticketService.getAllTickets();
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketService.getTicketById(id, currentUserId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            @CurrentUserId Long currentUserId) {

        return ticketService.updateTicket(id, request, currentUserId);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public TicketResponse assignTicket(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request,
            @CurrentUserId Long currentUserId) {

        return ticketWorkflowService.assignTicket(id, request, currentUserId);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public TicketResponse startWork(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.startWork(id, currentUserId);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public TicketResponse resolveTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.resolveTicket(id, currentUserId);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse reopenTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.reopenTicket(id, currentUserId);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORG_ADMIN')")
    public TicketResponse closeTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.closeTicket(id, currentUserId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public void deleteTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        ticketService.deleteTicket(id, currentUserId);
    }
}
