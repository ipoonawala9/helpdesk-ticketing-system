package com.ibrahim.helpdesk.exception;

/**
 * Thrown when a request is well-formed but violates a HelpDesk business rule,
 * for example creating an organization-scoped user without an organization.
 * Maps to HTTP 400.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
