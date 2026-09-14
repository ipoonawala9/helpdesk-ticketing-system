package com.ibrahim.helpdesk.message.controller;

import com.ibrahim.helpdesk.message.dto.MessageResponse;
import com.ibrahim.helpdesk.message.dto.PostMessageRequest;
import com.ibrahim.helpdesk.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse postMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody PostMessageRequest request) {

        return messageService.postMessage(ticketId, request);
    }

    /**
     * {@code userId} identifies the reader only until authentication exists.
     */
    @GetMapping
    public List<MessageResponse> getMessages(
            @PathVariable Long ticketId,
            @RequestParam Long userId) {

        return messageService.getMessages(ticketId, userId);
    }
}
