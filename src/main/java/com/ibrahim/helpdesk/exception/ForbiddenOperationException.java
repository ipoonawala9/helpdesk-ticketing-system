package com.ibrahim.helpdesk.exception;

/**
 * Thrown when the acting user is identified but is not permitted to perform the
 * requested action, for example a non-admin trying to assign a ticket.
 * Maps to HTTP 403.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
