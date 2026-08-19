package com.ibrahim.helpdesk.user.dto;

import com.ibrahim.helpdesk.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for user creation. Only the fields a caller is allowed to
 * supply are present: the User entity is never bound directly, so id, active
 * and any future internal field cannot be mass-assigned.
 *
 * <p>Role and organizationId are still accepted here because there is no
 * authentication yet; both become server-derived in the authentication and
 * tenant-isolation phases.
 */
public record CreateUserRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 200, message = "Email must be at most 200 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @Pattern(
                regexp = "^$|^[+]?[0-9 ()-]{7,20}$",
                message = "Phone number must be 7 to 20 characters and may contain digits, spaces, +, - and ()"
        )
        String phoneNumber,

        @NotNull(message = "Role is required")
        UserRole role,

        Long organizationId
) {
}
