package com.ibrahim.helpdesk.ticket.controller;

import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.organization.dto.OrganizationSummaryResponse;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.user.dto.UserSummaryResponse;
import com.ibrahim.helpdesk.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
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
}
