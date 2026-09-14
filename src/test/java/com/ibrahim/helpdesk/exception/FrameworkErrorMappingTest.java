package com.ibrahim.helpdesk.exception;

import com.ibrahim.helpdesk.security.config.SecurityConfig;
import com.ibrahim.helpdesk.ticket.controller.TicketController;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.ticket.service.TicketWorkflowService;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.ibrahim.helpdesk.support.WebSliceSecurity.as;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errors raised by Spring MVC itself, before any controller runs, must keep
 * their real HTTP status rather than falling into the catch-all 500 handler.
 */
@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
class FrameworkErrorMappingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketWorkflowService ticketWorkflowService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("an unknown URL is a 404 in the standard error shape")
    void unknownUrlIsNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(as(1L, UserRole.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No endpoint GET /api/does-not-exist"))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"));
    }

    @Test
    @DisplayName("an unsupported HTTP method is a 405")
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(patch("/api/tickets/42").with(as(1L, UserRole.CUSTOMER)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value(containsString("PATCH")));
    }

    @Test
    @DisplayName("an unsupported content type is a 415")
    void unsupportedMediaTypeIs415() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .with(as(1L, UserRole.CUSTOMER))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("framework error messages do not leak Java class names")
    void messagesDoNotLeakInternals() throws Exception {
        mockMvc.perform(patch("/api/tickets/42").with(as(1L, UserRole.CUSTOMER)))
                .andExpect(jsonPath("$.message").value(not(containsString("Exception"))))
                .andExpect(jsonPath("$.message").value(not(containsString("org.springframework"))));
    }
}
