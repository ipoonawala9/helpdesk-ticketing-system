package com.ibrahim.helpdesk.exception;

import java.util.Map;

/**
 * A request that is well-formed but has a field the server rejects, such as a
 * wrong current password. Reported as a 400 in the same shape as Bean
 * Validation failures, so clients show it next to the field.
 */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String field, String message) {
        super("Validation failed");
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
