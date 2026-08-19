package com.ibrahim.helpdesk.organization.dto;

/**
 * Minimal organization view used when an organization is nested inside another
 * response (a ticket or a user). Keeps payloads flat and avoids leaking
 * contact details to every caller that happens to read a ticket.
 */
public record OrganizationSummaryResponse(
        Long id,
        String name
) {
}
