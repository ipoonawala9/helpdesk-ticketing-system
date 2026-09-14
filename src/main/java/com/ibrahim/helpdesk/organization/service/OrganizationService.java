package com.ibrahim.helpdesk.organization.service;

import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.common.paging.PageResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public PageResponse<OrganizationResponse> listOrganizations(String query, Pageable pageable) {
        String term = PageRequests.searchTerm(query);
        Specification<Organization> search = (root, criteriaQuery, cb) -> {
            if (term == null) {
                return cb.conjunction();
            }
            String pattern = PageRequests.containsPattern(term);
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("domain")), pattern, '\\'));
        };
        return PageResponse.of(organizationRepository.findAll(search, pageable), OrganizationMapper::toResponse);
    }

    /** API sort names and the organization property each one sorts on. */
    public static final java.util.Map<String, String> SORTABLE_FIELDS = java.util.Map.of(
            "name", "name",
            "domain", "domain",
            "industry", "industry");

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
