package com.ibrahim.helpdesk.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Reset token is required")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
        String newPassword
) {

    /** Keeps the token and password out of logs and error messages. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[token=****, newPassword=****]";
    }
}
