package com.ibrahim.helpdesk.user.dto;

import com.ibrahim.helpdesk.organization.dto.OrganizationSummaryResponse;
import com.ibrahim.helpdesk.user.entity.UserRole;

/**
 * Safe outward-facing view of a User. The password field is intentionally
 * absent from this record so it cannot be serialised even by accident.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String phoneNumber,
        UserRole role,
        Boolean active,
        OrganizationSummaryResponse organization
) {
}
