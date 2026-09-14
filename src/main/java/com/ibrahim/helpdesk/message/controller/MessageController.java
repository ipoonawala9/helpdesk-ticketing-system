package com.ibrahim.helpdesk.message.controller;

import com.ibrahim.helpdesk.message.dto.MessageResponse;
import com.ibrahim.helpdesk.message.dto.PostMessageRequest;
import com.ibrahim.helpdesk.message.service.MessageService;
import com.ibrahim.helpdesk.security.auth.CurrentUserId;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Messages", description = "The conversation on a ticket between its customer and assigned agent")
@RequestMapping("/api/tickets/{ticketId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "Post a message on a ticket, as its customer or assigned agent")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPPORT_AGENT')")
    public MessageResponse postMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody PostMessageRequest request,
            @CurrentUserId Long currentUserId) {

        return messageService.postMessage(ticketId, request, currentUserId);
    }

    @Operation(summary = "Read a ticket's conversation, oldest first")
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPPORT_AGENT', 'ORG_ADMIN')")
    public List<MessageResponse> getMessages(@PathVariable Long ticketId, @CurrentUserId Long currentUserId) {
        return messageService.getMessages(ticketId, currentUserId);
    }
}
