package com.ibrahim.helpdesk.ticket.controller;

import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.common.paging.PageResponse;
import com.ibrahim.helpdesk.security.auth.CurrentUserId;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.TicketSearchCriteria;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.ticket.service.TicketWorkflowService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * {@code @PreAuthorize} states which roles may call each endpoint. Whether the
 * caller is the right user for this particular ticket, such as its customer or
 * its assigned agent, is checked in the services.
 */
@RestController
@Tag(name = "Tickets", description = "Ticket creation, reading, editing and the ticket workflow")
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketWorkflowService ticketWorkflowService;

    @Operation(summary = "Open a ticket as the authenticated customer")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @CurrentUserId Long currentUserId) {

        return ticketService.createTicket(request, currentUserId);
    }

    /** A page of the tickets within the caller's scope; see TicketService#listTickets. */
    @Operation(summary = "List tickets within the caller's scope, with filters, search, sorting and paging")
    @GetMapping
    public PageResponse<TicketResponse> listTickets(
            @RequestParam(required = false) Set<TicketStatus> status,
            @RequestParam(required = false) Set<TicketPriority> priority,
            @RequestParam(required = false) Set<TicketCategory> category,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @CurrentUserId Long currentUserId) {

        TicketSearchCriteria criteria = new TicketSearchCriteria(
                status == null ? Set.of() : status,
                priority == null ? Set.of() : priority,
                category == null ? Set.of() : category,
                customerId, assignedAgentId, unassigned, organizationId, q);

        return ticketService.listTickets(
                currentUserId, criteria, PageRequests.of(page, size, sort, TicketService.SORTABLE_FIELDS));
    }

    @Operation(summary = "Get one ticket within the caller's scope")
    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketService.getTicketById(id, currentUserId);
    }

    @Operation(summary = "Edit the title, description and category of your own ticket")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            @CurrentUserId Long currentUserId) {

        return ticketService.updateTicket(id, request, currentUserId);
    }

    @Operation(summary = "Assign or reassign a ticket to an agent of the same organization")
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public TicketResponse assignTicket(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request,
            @CurrentUserId Long currentUserId) {

        return ticketWorkflowService.assignTicket(id, request, currentUserId);
    }

    @Operation(summary = "Start work on a ticket assigned to you")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public TicketResponse startWork(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.startWork(id, currentUserId);
    }

    @Operation(summary = "Resolve a ticket assigned to you")
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('SUPPORT_AGENT')")
    public TicketResponse resolveTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.resolveTicket(id, currentUserId);
    }

    @Operation(summary = "Reopen your resolved ticket, or your closed ticket within the reopen window")
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TicketResponse reopenTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.reopenTicket(id, currentUserId);
    }

    @Operation(summary = "Close a resolved ticket, as its customer or as an organization admin after the grace period")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORG_ADMIN')")
    public TicketResponse closeTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return ticketWorkflowService.closeTicket(id, currentUserId);
    }

    @Operation(summary = "Delete a ticket of your organization")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public void deleteTicket(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        ticketService.deleteTicket(id, currentUserId);
    }
}
