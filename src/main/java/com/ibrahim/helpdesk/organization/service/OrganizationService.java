package com.ibrahim.helpdesk.organization.service;

import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.organization.dto.CreateOrganizationRequest;
import com.ibrahim.helpdesk.organization.dto.OrganizationResponse;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.mapper.OrganizationMapper;
import com.ibrahim.helpdesk.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        Organization saved = organizationRepository.save(OrganizationMapper.toEntity(request));
        return OrganizationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getAllOrganizations() {
        return organizationRepository.findAll()
                .stream()
                .map(OrganizationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(Long id) {
        return OrganizationMapper.toResponse(findOrThrow(id));
    }

    /**
     * Entity-level lookup for other services that need to attach an
     * organization to something. Not exposed through any controller.
     */
    @Transactional(readOnly = true)
    public Organization findOrThrow(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
    }
}
