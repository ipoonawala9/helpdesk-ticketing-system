package com.ibrahim.helpdesk.organization.service;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;

import java.util.List;
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }
    public Organization saveOrganization(Organization organization) {
        return organizationRepository.save(organization);
    }
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }
    public Organization getOrganizationById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(()-> new OrganizationNotFoundException(id));
    }
}
