package com.ibrahim.helpdesk.message.controller;

import com.ibrahim.helpdesk.message.dto.MessageResponse;
import com.ibrahim.helpdesk.message.dto.PostMessageRequest;
import com.ibrahim.helpdesk.message.service.MessageService;
import com.ibrahim.helpdesk.security.auth.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPPORT_AGENT')")
    public MessageResponse postMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody PostMessageRequest request,
            @CurrentUserId Long currentUserId) {

        return messageService.postMessage(ticketId, request, currentUserId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SUPPORT_AGENT', 'ORG_ADMIN')")
    public List<MessageResponse> getMessages(@PathVariable Long ticketId, @CurrentUserId Long currentUserId) {
        return messageService.getMessages(ticketId, currentUserId);
    }
}
