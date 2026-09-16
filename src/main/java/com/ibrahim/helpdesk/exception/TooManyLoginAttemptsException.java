package com.ibrahim.helpdesk.exception;

import java.time.Duration;

/**
 * Sign-in for an email is temporarily blocked after repeated wrong passwords.
 * Maps to HTTP 429 with a Retry-After header.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed sign-in attempts. Try again in " + minutes(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    private static String minutes(Duration duration) {
        long minutes = Math.max(1, (duration.getSeconds() + 59) / 60);
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
