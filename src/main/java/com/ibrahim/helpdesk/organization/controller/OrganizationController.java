package com.ibrahim.helpdesk.organization.controller;

import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.common.paging.PageResponse;
import com.ibrahim.helpdesk.organization.dto.CreateOrganizationRequest;
import com.ibrahim.helpdesk.organization.dto.OrganizationResponse;
import com.ibrahim.helpdesk.organization.service.OrganizationService;
import com.ibrahim.helpdesk.security.auth.CurrentUserId;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Organizations", description = "Tenants of the system")
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @Operation(summary = "Create an organization")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public OrganizationResponse createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {

        return organizationService.createOrganization(request);
    }

    @Operation(summary = "List organizations, with search, sorting and paging")
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<OrganizationResponse> listOrganizations(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {

        return organizationService.listOrganizations(q,
                PageRequests.of(page, size, sort, OrganizationService.SORTABLE_FIELDS));
    }

    @Operation(summary = "Get your own organization, or any organization as super admin")
    @GetMapping("/{id}")
    public OrganizationResponse getOrganizationById(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return organizationService.getOrganizationById(id, currentUserId);
    }
}
