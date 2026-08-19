package com.ibrahim.helpdesk.user.dto;

import com.ibrahim.helpdesk.user.entity.UserRole;

/**
 * Minimal user view used when a user is nested inside another response, such
 * as the customer or assigned agent on a ticket.
 */
public record UserSummaryResponse(
        Long id,
        String name,
        String email,
        UserRole role
) {
}
