package com.ibrahim.helpdesk.exception;

import com.ibrahim.helpdesk.ticket.entity.TicketStatus;

/**
 * Thrown when a workflow action is not allowed from the ticket's current
 * status, such as assigning a ticket that is already resolved. Maps to HTTP 409
 * because the request is valid but conflicts with the ticket's state.
 */
public class InvalidTicketStateException extends RuntimeException {

    public InvalidTicketStateException(String action, TicketStatus currentStatus) {
        super("Cannot " + action + " a ticket with status " + currentStatus);
    }
}
