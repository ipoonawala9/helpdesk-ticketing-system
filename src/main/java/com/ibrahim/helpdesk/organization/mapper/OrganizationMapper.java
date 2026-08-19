package com.ibrahim.helpdesk.organization.mapper;

import com.ibrahim.helpdesk.organization.dto.CreateOrganizationRequest;
import com.ibrahim.helpdesk.organization.dto.OrganizationResponse;
import com.ibrahim.helpdesk.organization.dto.OrganizationSummaryResponse;
import com.ibrahim.helpdesk.organization.entity.Organization;

/**
 * Explicit entity <-> DTO mapping. Deliberately hand written so that adding a
 * field to an entity never silently widens an API response.
 */
public final class OrganizationMapper {

    private OrganizationMapper() {
    }

    public static Organization toEntity(CreateOrganizationRequest request) {
        Organization organization = new Organization();
        organization.setName(request.name());
        organization.setCompanyEmail(request.companyEmail());
        organization.setDomain(request.domain());
        organization.setIndustry(request.industry());
        return organization;
    }

    public static OrganizationResponse toResponse(Organization organization) {
        if (organization == null) {
            return null;
        }
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCompanyEmail(),
                organization.getDomain(),
                organization.getIndustry()
        );
    }

    public static OrganizationSummaryResponse toSummary(Organization organization) {
        if (organization == null) {
            return null;
        }
        return new OrganizationSummaryResponse(
                organization.getId(),
                organization.getName()
        );
    }
}
