package com.ibrahim.helpdesk.organization.service;

import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.organization.dto.CreateOrganizationRequest;
import com.ibrahim.helpdesk.organization.dto.OrganizationResponse;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.mapper.OrganizationMapper;
import com.ibrahim.helpdesk.organization.repository.OrganizationRepository;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

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

    /**
     * A SUPER_ADMIN may view any organization; everyone else only their own.
     * Another organization is reported as not found, so its existence is not
     * revealed.
     */
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(Long id, Long viewerId) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new UserNotFoundException(viewerId));

        boolean member = viewer.getOrganization() != null
                && Objects.equals(viewer.getOrganization().getId(), id);
        if (viewer.getRole() != UserRole.SUPER_ADMIN && !member) {
            throw new OrganizationNotFoundException(id);
        }
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
