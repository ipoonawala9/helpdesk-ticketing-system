package com.ibrahim.helpdesk.ticket.controller;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.organization.dto.OrganizationSummaryResponse;
import com.ibrahim.helpdesk.ticket.dto.AgentActionRequest;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CloseTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.ReopenTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.ticket.service.TicketWorkflowService;
import com.ibrahim.helpdesk.user.dto.UserSummaryResponse;
import com.ibrahim.helpdesk.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketWorkflowService ticketWorkflowService;

    private TicketResponse sampleResponse() {
        return new TicketResponse(
                42L, "HD-2026-000042", "Printer will not print", "It jams on every job",
                TicketStatus.OPEN, null, TicketCategory.HARDWARE,
                new UserSummaryResponse(1L, "Dana Customer", "dana@acme.test", UserRole.CUSTOMER),
                null,
                new OrganizationSummaryResponse(7L, "Acme Ltd"),
                0, LocalDateTime.now(), LocalDateTime.now(), null, null);
    }

    @Test
    @DisplayName("POST /api/tickets returns 201 and a ticket free of entity internals")
    void createTicketReturnsCreated() throws Exception {
        when(ticketService.createTicket(any(CreateTicketRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Printer will not print",
                                 "description":"It jams on every job",
                                 "category":"HARDWARE","customerId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("HD-2026-000042"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedAgent").doesNotExist())
                .andExpect(jsonPath("$.customer.email").value("dana@acme.test"))
                .andExpect(jsonPath("$.customer.password").doesNotExist())
                .andExpect(jsonPath("$.customer.organization").doesNotExist())
                .andExpect(jsonPath("$.organization.companyEmail").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/tickets rejects a blank body with 400 and per-field messages")
    void createTicketRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","description":"","category":null,"customerId":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/tickets"))
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.description").value("Description is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"))
                .andExpect(jsonPath("$.fieldErrors.customerId").value("Customer id is required"));

        verify(ticketService, never()).createTicket(any(CreateTicketRequest.class));
    }

    @Test
    @DisplayName("POST /api/tickets rejects an unknown category with 400 rather than 500")
    void createTicketRejectsUnknownCategory() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","description":"d","category":"BANANA","customerId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    @DisplayName("GET /api/tickets/{id} returns the standard error shape when missing")
    void getTicketByIdReturnsNotFound() throws Exception {
        when(ticketService.getTicketById(404L)).thenThrow(new TicketNotFoundException(404L));

        mockMvc.perform(get("/api/tickets/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Ticket with ID 404 not found"))
                .andExpect(jsonPath("$.path").value("/api/tickets/404"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/tickets/{id} returns 204 with no body")
    void deleteTicketReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/tickets/42"))
                .andExpect(status().isNoContent());

        verify(ticketService).deleteTicket(eq(42L));
    }

    private static final String ASSIGN_BODY = """
            {"agentId":20,"adminId":10}
            """;

    @Test
    @DisplayName("POST /api/tickets/{id}/assign returns 200 with the assigned ticket")
    void assignTicketReturnsOk() throws Exception {
        TicketResponse assigned = new TicketResponse(
                42L, "HD-2026-000042", "Printer will not print", "It jams on every job",
                TicketStatus.ASSIGNED, null, TicketCategory.HARDWARE,
                new UserSummaryResponse(1L, "Dana Customer", "dana@acme.test", UserRole.CUSTOMER),
                new UserSummaryResponse(20L, "Sam Agent", "sam@acme.test", UserRole.SUPPORT_AGENT),
                new OrganizationSummaryResponse(7L, "Acme Ltd"),
                0, LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(ticketWorkflowService.assignTicket(eq(42L), any(AssignTicketRequest.class))).thenReturn(assigned);

        mockMvc.perform(post("/api/tickets/42/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value(20))
                .andExpect(jsonPath("$.assignedAgent.role").value("SUPPORT_AGENT"))
                .andExpect(jsonPath("$.assignedAgent.password").doesNotExist());

        verify(ticketWorkflowService).assignTicket(42L, new AssignTicketRequest(20L, 10L));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/assign validates that agentId and adminId are present")
    void assignTicketValidatesBody() throws Exception {
        mockMvc.perform(post("/api/tickets/42/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.agentId").value("Agent id is required"))
                .andExpect(jsonPath("$.fieldErrors.adminId").value("Admin id is required"));

        verify(ticketWorkflowService, never()).assignTicket(anyLong(), any(AssignTicketRequest.class));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/assign maps a forbidden actor to 403")
    void assignTicketMapsForbiddenTo403() throws Exception {
        when(ticketWorkflowService.assignTicket(eq(42L), any(AssignTicketRequest.class)))
                .thenThrow(new ForbiddenOperationException("Only organization administrators can assign tickets"));

        mockMvc.perform(post("/api/tickets/42/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Only organization administrators can assign tickets"))
                .andExpect(jsonPath("$.path").value("/api/tickets/42/assign"));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/assign maps a non-assignable status to 409")
    void assignTicketMapsInvalidStateTo409() throws Exception {
        when(ticketWorkflowService.assignTicket(eq(42L), any(AssignTicketRequest.class)))
                .thenThrow(new InvalidTicketStateException("assign", TicketStatus.CLOSED));

        mockMvc.perform(post("/api/tickets/42/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot assign a ticket with status CLOSED"));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/assign maps an invalid agent to 400")
    void assignTicketMapsInvalidAgentTo400() throws Exception {
        when(ticketWorkflowService.assignTicket(eq(42L), any(AssignTicketRequest.class)))
                .thenThrow(new BusinessRuleException("Agent must belong to the ticket's organization"));

        mockMvc.perform(post("/api/tickets/42/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Agent must belong to the ticket's organization"));
    }

    private static TicketResponse ticketWithStatus(TicketStatus status, LocalDateTime resolvedAt) {
        return new TicketResponse(
                42L, "HD-2026-000042", "Printer will not print", "It jams on every job",
                status, null, TicketCategory.HARDWARE,
                new UserSummaryResponse(1L, "Dana Customer", "dana@acme.test", UserRole.CUSTOMER),
                new UserSummaryResponse(20L, "Sam Agent", "sam@acme.test", UserRole.SUPPORT_AGENT),
                new OrganizationSummaryResponse(7L, "Acme Ltd"),
                0, LocalDateTime.now(), LocalDateTime.now(), resolvedAt, null);
    }

    private static final String AGENT_BODY = """
            {"agentId":20}
            """;

    @Test
    @DisplayName("POST /api/tickets/{id}/start returns 200 with the IN_PROGRESS ticket")
    void startWorkReturnsOk() throws Exception {
        when(ticketWorkflowService.startWork(eq(42L), any(AgentActionRequest.class)))
                .thenReturn(ticketWithStatus(TicketStatus.IN_PROGRESS, null));

        mockMvc.perform(post("/api/tickets/42/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        verify(ticketWorkflowService).startWork(42L, new AgentActionRequest(20L));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/resolve returns 200 with the RESOLVED ticket and resolvedAt")
    void resolveReturnsOk() throws Exception {
        when(ticketWorkflowService.resolveTicket(eq(42L), any(AgentActionRequest.class)))
                .thenReturn(ticketWithStatus(TicketStatus.RESOLVED, LocalDateTime.now()));

        mockMvc.perform(post("/api/tickets/42/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        verify(ticketWorkflowService).resolveTicket(42L, new AgentActionRequest(20L));
    }

    @ParameterizedTest(name = "POST /api/tickets/42/{0} requires agentId")
    @ValueSource(strings = {"start", "resolve"})
    void agentActionsValidateBody(String action) throws Exception {
        mockMvc.perform(post("/api/tickets/42/" + action)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.agentId").value("Agent id is required"));

        verify(ticketWorkflowService, never()).startWork(anyLong(), any(AgentActionRequest.class));
        verify(ticketWorkflowService, never()).resolveTicket(anyLong(), any(AgentActionRequest.class));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/start maps a non-assigned caller to 403")
    void startWorkMapsForbiddenTo403() throws Exception {
        when(ticketWorkflowService.startWork(eq(42L), any(AgentActionRequest.class)))
                .thenThrow(new ForbiddenOperationException("Only the assigned agent can start work on this ticket"));

        mockMvc.perform(post("/api/tickets/42/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the assigned agent can start work on this ticket"))
                .andExpect(jsonPath("$.path").value("/api/tickets/42/start"));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/resolve maps an invalid transition to 409")
    void resolveMapsInvalidStateTo409() throws Exception {
        when(ticketWorkflowService.resolveTicket(eq(42L), any(AgentActionRequest.class)))
                .thenThrow(new InvalidTicketStateException("resolve", TicketStatus.ASSIGNED));

        mockMvc.perform(post("/api/tickets/42/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AGENT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot resolve a ticket with status ASSIGNED"));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/reopen returns 200 with the REOPENED ticket")
    void reopenReturnsOk() throws Exception {
        when(ticketWorkflowService.reopenTicket(eq(42L), any(ReopenTicketRequest.class)))
                .thenReturn(ticketWithStatus(TicketStatus.REOPENED, null));

        mockMvc.perform(post("/api/tickets/42/reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));

        verify(ticketWorkflowService).reopenTicket(42L, new ReopenTicketRequest(1L));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/close returns 200 with the CLOSED ticket")
    void closeReturnsOk() throws Exception {
        when(ticketWorkflowService.closeTicket(eq(42L), any(CloseTicketRequest.class)))
                .thenReturn(ticketWithStatus(TicketStatus.CLOSED, LocalDateTime.now()));

        mockMvc.perform(post("/api/tickets/42/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        verify(ticketWorkflowService).closeTicket(42L, new CloseTicketRequest(1L));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/reopen requires customerId")
    void reopenValidatesBody() throws Exception {
        mockMvc.perform(post("/api/tickets/42/reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerId").value("Customer id is required"));

        verify(ticketWorkflowService, never()).reopenTicket(anyLong(), any(ReopenTicketRequest.class));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/close requires userId")
    void closeValidatesBody() throws Exception {
        mockMvc.perform(post("/api/tickets/42/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").value("User id is required"));

        verify(ticketWorkflowService, never()).closeTicket(anyLong(), any(CloseTicketRequest.class));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/close maps an early admin close to 409 with the reason")
    void closeMapsEarlyAdminCloseTo409() throws Exception {
        String reason = "The customer has 3 hours after resolution to close this ticket; "
                + "an administrator can close it from 2026-09-14T15:00";
        when(ticketWorkflowService.closeTicket(eq(42L), any(CloseTicketRequest.class)))
                .thenThrow(new InvalidTicketStateException(reason));

        mockMvc.perform(post("/api/tickets/42/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":10}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(reason));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/reopen maps a non-customer caller to 403")
    void reopenMapsForbiddenTo403() throws Exception {
        when(ticketWorkflowService.reopenTicket(eq(42L), any(ReopenTicketRequest.class)))
                .thenThrow(new ForbiddenOperationException("Only the customer who opened this ticket can reopen it"));

        mockMvc.perform(post("/api/tickets/42/reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":20}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value("/api/tickets/42/reopen"));
    }
}
