package com.ibrahim.helpdesk.message.service;

import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.message.dto.MessageResponse;
import com.ibrahim.helpdesk.message.dto.PostMessageRequest;
import com.ibrahim.helpdesk.message.entity.Message;
import com.ibrahim.helpdesk.message.mapper.MessageMapper;
import com.ibrahim.helpdesk.message.repository.MessageRepository;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.service.TicketService;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.ibrahim.helpdesk.ticket.service.TicketParticipants.isAssignedAgent;
import static com.ibrahim.helpdesk.ticket.service.TicketParticipants.isCustomer;
import static com.ibrahim.helpdesk.ticket.service.TicketParticipants.isOrgAdmin;

/**
 * Ticket-specific conversation between the customer and the assigned agent.
 * Organization administrators can read a ticket's conversation but do not take
 * part in it.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final TicketService ticketService;
    private final UserService userService;
    private final Clock clock;

    @Transactional
    public MessageResponse postMessage(Long ticketId, PostMessageRequest request, Long senderId) {

        User sender = userService.findOrThrow(senderId);
        Ticket ticket = ticketService.findVisibleOrThrow(ticketId, sender);

        if (!isCustomer(ticket, sender) && !isAssignedAgent(ticket, sender)) {
            throw new ForbiddenOperationException(
                    "Only the ticket's customer and its assigned agent can post messages");
        }
        requireActive(sender, "post messages");

        // A closed conversation stays readable; the customer reopens the
        // ticket to continue it.
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidTicketStateException(
                    "Cannot post a message on a CLOSED ticket; reopen it first");
        }

        Message message = new Message();
        message.setTicket(ticket);
        message.setSender(sender);
        message.setContent(request.content().strip());
        message.setCreatedAt(LocalDateTime.now(clock));

        return MessageMapper.toResponse(messageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long ticketId, Long userId) {

        User reader = userService.findOrThrow(userId);
        Ticket ticket = ticketService.findVisibleOrThrow(ticketId, reader);

        if (!isCustomer(ticket, reader) && !isAssignedAgent(ticket, reader) && !isOrgAdmin(ticket, reader)) {
            throw new ForbiddenOperationException(
                    "Only the ticket's customer, its assigned agent and administrators of its organization "
                            + "can read its messages");
        }
        requireActive(reader, "read messages");

        return messageRepository.findThread(ticket.getId())
                .stream()
                .map(MessageMapper::toResponse)
                .toList();
    }

    private static void requireActive(User user, String action) {
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ForbiddenOperationException("Inactive users cannot " + action);
        }
    }
}
