package com.ibrahim.helpdesk.ticket.controller;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.organization.dto.OrganizationSummaryResponse;
import com.ibrahim.helpdesk.security.config.SecurityConfig;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.ticket.service.TicketWorkflowService;
import com.ibrahim.helpdesk.user.dto.UserSummaryResponse;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.ibrahim.helpdesk.support.WebSliceSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
class TicketControllerTest {

    private static final long CUSTOMER = 1L;
    private static final long ADMIN = 10L;
    private static final long AGENT = 20L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketWorkflowService ticketWorkflowService;

    /** Needed by SecurityConfig's beans; slice requests are pre-authenticated, so it is never queried. */
    @MockitoBean
    private UserRepository userRepository;

    private static TicketResponse ticket(TicketStatus status) {
        return new TicketResponse(
                42L, "HD-2026-000042", "Printer will not print", "It jams on every job",
                status, null, TicketCategory.HARDWARE,
                new UserSummaryResponse(CUSTOMER, "Dana Customer", "dana@acme.test", UserRole.CUSTOMER),
                status == TicketStatus.OPEN ? null
                        : new UserSummaryResponse(AGENT, "Sam Agent", "sam@acme.test", UserRole.SUPPORT_AGENT),
                new OrganizationSummaryResponse(7L, "Acme Ltd"),
                0, LocalDateTime.now(), LocalDateTime.now(), null, null);
    }

    // ----- authentication -----------------------------------------------------

    @Test
    @DisplayName("a request without a token is a 401 in the standard error shape, with a Bearer challenge")
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tickets/42"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/tickets/42"));

        verifyNoInteractions(ticketService);
    }

    @Test
    @DisplayName("a malformed bearer token is a 401 marked invalid_token")
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/tickets/42").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));
    }

    // ----- role rules ---------------------------------------------------------

    /**
     * Every role that may NOT call an endpoint is refused before the service is
     * reached. Allowed roles are exercised by the tests below.
     */
    @ParameterizedTest(name = "{0} {1} as {2} is forbidden")
    @CsvSource({
            "POST,   /api/tickets,             SUPPORT_AGENT",
            "POST,   /api/tickets,             ORG_ADMIN",
            "POST,   /api/tickets,             SUPER_ADMIN",
            "PUT,    /api/tickets/42,          SUPPORT_AGENT",
            "PUT,    /api/tickets/42,          ORG_ADMIN",
            "DELETE, /api/tickets/42,          CUSTOMER",
            "DELETE, /api/tickets/42,          SUPPORT_AGENT",
            "POST,   /api/tickets/42/assign,   CUSTOMER",
            "POST,   /api/tickets/42/assign,   SUPPORT_AGENT",
            "POST,   /api/tickets/42/assign,   SUPER_ADMIN",
            "POST,   /api/tickets/42/start,    CUSTOMER",
            "POST,   /api/tickets/42/start,    ORG_ADMIN",
            "POST,   /api/tickets/42/resolve,  CUSTOMER",
            "POST,   /api/tickets/42/resolve,  ORG_ADMIN",
            "POST,   /api/tickets/42/reopen,   SUPPORT_AGENT",
            "POST,   /api/tickets/42/reopen,   ORG_ADMIN",
            "POST,   /api/tickets/42/close,    SUPPORT_AGENT",
            "POST,   /api/tickets/42/close,    SUPER_ADMIN"
    })
    void wrongRoleIsForbidden(String method, String path, UserRole role) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .with(as(99L, role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","description":"d","category":"OTHER","agentId":20}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));

        verifyNoInteractions(ticketService, ticketWorkflowService);
    }

    // ----- create, read, update, delete ---------------------------------------

    @Test
    @DisplayName("POST /api/tickets returns 201 and passes the authenticated customer's id to the service")
    void createTicketReturnsCreated() throws Exception {
        when(ticketService.createTicket(any(CreateTicketRequest.class), eq(CUSTOMER))).thenReturn(ticket(TicketStatus.OPEN));

        mockMvc.perform(post("/api/tickets")
                        .with(as(CUSTOMER, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Printer will not print","description":"It jams on every job","category":"HARDWARE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("HD-2026-000042"))
                .andExpect(jsonPath("$.assignedAgent").doesNotExist())
                .andExpect(jsonPath("$.customer.password").doesNotExist())
                .andExpect(jsonPath("$.organization.companyEmail").doesNotExist());

        verify(ticketService).createTicket(
                new CreateTicketRequest("Printer will not print", "It jams on every job", TicketCategory.HARDWARE),
                CUSTOMER);
    }

    @Test
    @DisplayName("POST /api/tickets rejects a blank body with 400 and per-field messages")
    void createTicketRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .with(as(CUSTOMER, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","description":"","category":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.description").value("Description is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"));

        verify(ticketService, never()).createTicket(any(), anyLong());
    }

    @Test
    @DisplayName("POST /api/tickets rejects an unknown category with 400 rather than 500")
    void createTicketRejectsUnknownCategory() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .with(as(CUSTOMER, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","description":"d","category":"BANANA"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @ParameterizedTest(name = "GET /api/tickets as {0} passes the caller's id to the scoped list")
    @org.junit.jupiter.params.provider.EnumSource(UserRole.class)
    void listForEveryRole(UserRole role) throws Exception {
        when(ticketService.listTickets(77L)).thenReturn(List.of(ticket(TicketStatus.OPEN)));

        mockMvc.perform(get("/api/tickets").with(as(77L, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42));

        verify(ticketService).listTickets(77L);
    }

    @Test
    @DisplayName("GET /api/tickets/{id} passes the viewer's id and returns the standard 404 shape when missing")
    void getTicketByIdReturnsNotFound() throws Exception {
        when(ticketService.getTicketById(404L, AGENT)).thenThrow(new TicketNotFoundException(404L));

        mockMvc.perform(get("/api/tickets/404").with(as(AGENT, UserRole.SUPPORT_AGENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Ticket with ID 404 not found"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/tickets/{id} passes the editing customer's id")
    void updateTicketPassesEditor() throws Exception {
        when(ticketService.updateTicket(eq(42L), any(UpdateTicketRequest.class), eq(CUSTOMER)))
                .thenReturn(ticket(TicketStatus.OPEN));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/tickets/42")
                        .with(as(CUSTOMER, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New","description":"New","category":"SOFTWARE"}
                                """))
                .andExpect(status().isOk());

        verify(ticketService).updateTicket(42L, new UpdateTicketRequest("New", "New", TicketCategory.SOFTWARE), CUSTOMER);
    }

    @Test
    @DisplayName("DELETE /api/tickets/{id} returns 204 for an ORG_ADMIN and passes their id")
    void deleteTicketReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/tickets/42").with(as(ADMIN, UserRole.ORG_ADMIN)))
                .andExpect(status().isNoContent());

        verify(ticketService).deleteTicket(42L, ADMIN);
    }

    // ----- workflow -------------------------------------------------------------

    @Test
    @DisplayName("POST /assign returns 200 and passes the admin's id with the agent from the body")
    void assignTicketReturnsOk() throws Exception {
        when(ticketWorkflowService.assignTicket(eq(42L), any(AssignTicketRequest.class), eq(ADMIN)))
                .thenReturn(ticket(TicketStatus.ASSIGNED));

        mockMvc.perform(post("/api/tickets/42/assign")
                        .with(as(ADMIN, UserRole.ORG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value(20))
                .andExpect(jsonPath("$.assignedAgent.password").doesNotExist());

        verify(ticketWorkflowService).assignTicket(42L, new AssignTicketRequest(20L), ADMIN);
    }

    @Test
    @DisplayName("POST /assign requires agentId")
    void assignTicketValidatesBody() throws Exception {
        mockMvc.perform(post("/api/tickets/42/assign")
                        .with(as(ADMIN, UserRole.ORG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.agentId").value("Agent id is required"));

        verifyNoInteractions(ticketWorkflowService);
    }

    @Test
    @DisplayName("POST /assign maps service rejections to 403, 409 and 400")
    void assignTicketMapsServiceErrors() throws Exception {
        when(ticketWorkflowService.assignTicket(eq(42L), any(AssignTicketRequest.class), eq(ADMIN)))
                .thenThrow(new ForbiddenOperationException("Administrators can only assign tickets from their own organization"))
                .thenThrow(new InvalidTicketStateException("assign", TicketStatus.CLOSED))
                .thenThrow(new BusinessRuleException("Agent must belong to the ticket's organization"));

        var assign = post("/api/tickets/42/assign")
                .with(as(ADMIN, UserRole.ORG_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"agentId":20}
                        """);

        mockMvc.perform(assign).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Administrators can only assign tickets from their own organization"));
        mockMvc.perform(assign).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot assign a ticket with status CLOSED"));
        mockMvc.perform(assign).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Agent must belong to the ticket's organization"));
    }

    @Test
    @DisplayName("start and resolve need no body and pass the agent's id")
    void startAndResolve() throws Exception {
        when(ticketWorkflowService.startWork(42L, AGENT)).thenReturn(ticket(TicketStatus.IN_PROGRESS));
        when(ticketWorkflowService.resolveTicket(42L, AGENT)).thenReturn(ticket(TicketStatus.RESOLVED));

        mockMvc.perform(post("/api/tickets/42/start").with(as(AGENT, UserRole.SUPPORT_AGENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/tickets/42/resolve").with(as(AGENT, UserRole.SUPPORT_AGENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("reopen passes the customer's id; close accepts both a customer and an org admin")
    void reopenAndClose() throws Exception {
        when(ticketWorkflowService.reopenTicket(42L, CUSTOMER)).thenReturn(ticket(TicketStatus.REOPENED));
        when(ticketWorkflowService.closeTicket(eq(42L), anyLong())).thenReturn(ticket(TicketStatus.CLOSED));

        mockMvc.perform(post("/api/tickets/42/reopen").with(as(CUSTOMER, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));
        mockMvc.perform(post("/api/tickets/42/close").with(as(CUSTOMER, UserRole.CUSTOMER)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/42/close").with(as(ADMIN, UserRole.ORG_ADMIN)))
                .andExpect(status().isOk());

        verify(ticketWorkflowService).closeTicket(42L, CUSTOMER);
        verify(ticketWorkflowService).closeTicket(42L, ADMIN);
    }

    @Test
    @DisplayName("an early admin close is reported as 409 with the reason")
    void closeMapsEarlyAdminCloseTo409() throws Exception {
        String reason = "The customer has 3 hours after resolution to close this ticket; "
                + "an administrator can close it from 2026-09-14T15:00";
        when(ticketWorkflowService.closeTicket(42L, ADMIN)).thenThrow(new InvalidTicketStateException(reason));

        mockMvc.perform(post("/api/tickets/42/close").with(as(ADMIN, UserRole.ORG_ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(reason));
    }
}
