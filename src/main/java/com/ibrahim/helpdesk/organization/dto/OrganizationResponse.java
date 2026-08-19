package com.ibrahim.helpdesk.organization.dto;

/**
 * Safe outward-facing view of an Organization. Never exposes the JPA entity
 * itself, so no Hibernate proxy fields or back-references can leak.
 */
public record OrganizationResponse(
        Long id,
        String name,
        String companyEmail,
        String domain,
        String industry
) {
}
