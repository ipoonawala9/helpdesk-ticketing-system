package com.ibrahim.helpdesk.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @NotBlank(message = "Company email is required")
        @Email(message = "Company email must be a valid email address")
        @Size(max = 200, message = "Company email must be at most 200 characters")
        String companyEmail,

        @NotBlank(message = "Domain is required")
        @Size(max = 150, message = "Domain must be at most 150 characters")
        String domain,

        @NotBlank(message = "Industry is required")
        @Size(max = 100, message = "Industry must be at most 100 characters")
        String industry
) {
}
