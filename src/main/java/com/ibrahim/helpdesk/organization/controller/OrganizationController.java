package com.ibrahim.helpdesk.organization.controller;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.service.OrganizationService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private OrganizationService organizationService;
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }
    @PostMapping
    public Organization createOrganization(@Valid @RequestBody Organization organization) {
        return organizationService.saveOrganization(organization);
    }
    @GetMapping("/{id}")
    public Organization getOrganizationById(@PathVariable Long id)

     {
        return organizationService.getOrganizationById(id);
    }

}
