package com.ibrahim.helpdesk.security.auth;

/**
 * A failed login. Deliberately says nothing about whether the email exists,
 * the password was wrong, or the account is inactive. Maps to HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
