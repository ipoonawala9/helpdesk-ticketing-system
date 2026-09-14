package com.ibrahim.helpdesk.message.controller;

import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.message.dto.MessageResponse;
import com.ibrahim.helpdesk.message.dto.PostMessageRequest;
import com.ibrahim.helpdesk.message.service.MessageService;
import com.ibrahim.helpdesk.security.config.SecurityConfig;
import com.ibrahim.helpdesk.user.dto.UserSummaryResponse;
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

import java.time.LocalDateTime;
import java.util.List;

import static com.ibrahim.helpdesk.support.WebSliceSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@Import(SecurityConfig.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    /** Needed by SecurityConfig's beans; never queried by pre-authenticated slice requests. */
    @MockitoBean
    private UserRepository userRepository;

    private static MessageResponse message(long id, String content) {
        return new MessageResponse(id, 42L,
                new UserSummaryResponse(1L, "Dana Customer", "dana@acme.test", UserRole.CUSTOMER),
                content, LocalDateTime.of(2026, 9, 14, 12, 0));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/messages returns 201 with the message")
    void postReturnsCreated() throws Exception {
        when(messageService.postMessage(eq(42L), any(PostMessageRequest.class), eq(1L))).thenReturn(message(7L, "Hello"));

        mockMvc.perform(post("/api/tickets/42/messages")
                        .with(as(1L, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello","senderId":999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.ticketId").value(42))
                .andExpect(jsonPath("$.sender.name").value("Dana Customer"))
                .andExpect(jsonPath("$.sender.password").doesNotExist())
                .andExpect(jsonPath("$.content").value("Hello"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T12:00:00"));

        // The sender is the authenticated user; a senderId in the body is ignored.
        verify(messageService).postMessage(42L, new PostMessageRequest("Hello"), 1L);
    }

    @Test
    @DisplayName("POST rejects blank content")
    void postValidatesBody() throws Exception {
        mockMvc.perform(post("/api/tickets/42/messages")
                        .with(as(1L, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").value("Content is required"));

        verify(messageService, never()).postMessage(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("POST rejects content over 5000 characters")
    void postRejectsLongContent() throws Exception {
        mockMvc.perform(post("/api/tickets/42/messages")
                        .with(as(1L, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted("x".repeat(5001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").value("Content must be at most 5000 characters"));
    }

    @Test
    @DisplayName("GET /api/tickets/{id}/messages returns the thread in order")
    void getReturnsThread() throws Exception {
        when(messageService.getMessages(42L, 1L)).thenReturn(List.of(message(1L, "First"), message(2L, "Second")));

        mockMvc.perform(get("/api/tickets/42/messages").with(as(1L, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("First"))
                .andExpect(jsonPath("$[1].content").value("Second"));
    }

    @Test
    @DisplayName("an org admin can read but not post; a super admin can do neither; no token is a 401")
    void roleRules() throws Exception {
        when(messageService.getMessages(42L, 10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/42/messages").with(as(10L, UserRole.ORG_ADMIN)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/42/messages")
                        .with(as(10L, UserRole.ORG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hi"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/42/messages").with(as(100L, UserRole.SUPER_ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/tickets/42/messages"))
                .andExpect(status().isUnauthorized());

        verify(messageService, never()).postMessage(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("POST maps a non-participant to 403 and a closed ticket to 409")
    void postMapsErrors() throws Exception {
        when(messageService.postMessage(eq(42L), any(PostMessageRequest.class), eq(20L)))
                .thenThrow(new ForbiddenOperationException("Only the ticket's customer and its assigned agent can post messages"))
                .thenThrow(new InvalidTicketStateException("Cannot post a message on a CLOSED ticket; reopen it first"));

        String body = """
                {"content":"Hi"}
                """;
        mockMvc.perform(post("/api/tickets/42/messages").with(as(20L, UserRole.SUPPORT_AGENT))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value("/api/tickets/42/messages"));

        mockMvc.perform(post("/api/tickets/42/messages").with(as(20L, UserRole.SUPPORT_AGENT))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot post a message on a CLOSED ticket; reopen it first"));
    }
}
