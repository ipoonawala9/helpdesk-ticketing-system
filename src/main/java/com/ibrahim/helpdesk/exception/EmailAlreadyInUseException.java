package com.ibrahim.helpdesk.exception;

/**
 * Thrown when creating a user with an email that already belongs to an
 * account. Email is the login name, so it must be unique. Maps to HTTP 409.
 */
public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException() {
        super("An account with this email already exists");
    }
}
