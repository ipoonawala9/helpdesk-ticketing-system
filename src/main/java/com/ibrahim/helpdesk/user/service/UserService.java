package com.ibrahim.helpdesk.user.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.EmailAlreadyInUseException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.common.paging.PageResponse;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.service.OrganizationService;
import com.ibrahim.helpdesk.user.dto.CreateUserRequest;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.mapper.UserMapper;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import com.ibrahim.helpdesk.user.repository.UserSpecifications;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    /** The only roles an organization administrator may create. */
    private static final Set<UserRole> ORG_ADMIN_CREATABLE_ROLES =
            EnumSet.of(UserRole.CUSTOMER, UserRole.SUPPORT_AGENT);

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a user on behalf of an administrator. The entity is built here
     * rather than bound from the request body, so id and active cannot be set
     * by a caller. The email is stored lower-case and must be unused; the
     * password is stored only as a hash.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long creatorId) {

        User creator = findOrThrow(creatorId);
        Organization organization = switch (creator.getRole()) {
            case SUPER_ADMIN -> organizationChosenBySuperAdmin(request);
            case ORG_ADMIN -> organizationOfOrgAdmin(request, creator);
            default -> throw new ForbiddenOperationException("Only administrators can create users");
        };

        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyInUseException();
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role());
        user.setOrganization(organization);
        user.setActive(true);

        return UserMapper.toResponse(userRepository.save(user));
    }

    /**
     * A user may view their own profile. A SUPER_ADMIN may view anyone, and an
     * ORG_ADMIN may view users of their own organization. Any other user is
     * reported as not found, so ids outside the viewer's scope reveal nothing.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id, Long viewerId) {
        User viewer = findOrThrow(viewerId);

        if (Objects.equals(id, viewer.getId())) {
            return UserMapper.toResponse(viewer);
        }
        User target = switch (viewer.getRole()) {
            case SUPER_ADMIN -> findOrThrow(id);
            case ORG_ADMIN -> viewer.getOrganization() == null
                    ? null
                    : findInOrganizationOrThrow(id, viewer.getOrganization().getId());
            case SUPPORT_AGENT, CUSTOMER -> null;
        };
        if (target == null) {
            throw new UserNotFoundException(id);
        }
        return UserMapper.toResponse(target);
    }

    /** API sort names and the user property each one sorts on. */
    public static final java.util.Map<String, String> SORTABLE_FIELDS = java.util.Map.of(
            "name", "name",
            "email", "email",
            "role", "role");

    /**
     * A page of the users an administrator may see, optionally narrowed by
     * role, active flag and a search on name or email. A SUPER_ADMIN sees every
     * organization and may narrow to one; an ORG_ADMIN always sees only their
     * own, and naming any other organization is answered as if it did not
     * exist.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(
            Long viewerId, UserRole role, Long organizationId, Boolean active, String query, Pageable pageable) {

        User viewer = findOrThrow(viewerId);

        Long scope = switch (viewer.getRole()) {
            case SUPER_ADMIN -> organizationId;
            case ORG_ADMIN -> {
                Long own = viewer.getOrganization() == null ? null : viewer.getOrganization().getId();
                if (own == null || (organizationId != null && !organizationId.equals(own))) {
                    throw new OrganizationNotFoundException(organizationId);
                }
                yield own;
            }
            default -> throw new ForbiddenOperationException("Only administrators can list users");
        };

        var users = userRepository.findAll(
                UserSpecifications.matching(scope, role, active, PageRequests.searchTerm(query)), pageable);
        return PageResponse.of(users, UserMapper::toResponse);
    }

    /**
     * Blocks or restores a user's access. A deactivated user cannot sign in,
     * and any access token they already hold stops working on their next
     * request. Their tickets and messages are kept; an administrator
     * reassigns a deactivated agent's tickets.
     *
     * <p>A SUPER_ADMIN may change anyone except themselves. An ORG_ADMIN may
     * change agents and customers of their own organization; anyone else is
     * reported as not found or forbidden, as for other user operations.
     */
    @Transactional
    public UserResponse setActive(Long targetId, boolean active, Long actorId) {
        User actor = findOrThrow(actorId);
        if (Objects.equals(targetId, actor.getId())) {
            throw new BusinessRuleException("You cannot change whether your own account is active");
        }

        User target = switch (actor.getRole()) {
            case SUPER_ADMIN -> findOrThrow(targetId);
            case ORG_ADMIN -> {
                if (actor.getOrganization() == null) {
                    throw new UserNotFoundException(targetId);
                }
                User inOrganization = findInOrganizationOrThrow(targetId, actor.getOrganization().getId());
                if (!ORG_ADMIN_CREATABLE_ROLES.contains(inOrganization.getRole())) {
                    throw new ForbiddenOperationException(
                            "Organization administrators can only activate or deactivate agents and customers");
                }
                yield inOrganization;
            }
            default -> throw new ForbiddenOperationException("Only administrators can activate or deactivate users");
        };

        target.setActive(active);
        return UserMapper.toResponse(userRepository.save(target));
    }

    /** A user who belongs to the given organization; anyone else is reported as not found. */
    @Transactional(readOnly = true)
    public User findInOrganizationOrThrow(Long userId, Long organizationId) {
        return userRepository.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Entity-level lookup for other services. Not exposed through a controller.
     */
    @Transactional(readOnly = true)
    public User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private Organization organizationChosenBySuperAdmin(CreateUserRequest request) {
        if (request.organizationId() == null) {
            if (request.role() != UserRole.SUPER_ADMIN) {
                throw new BusinessRuleException(
                        "organizationId is required for role " + request.role());
            }
            return null;
        }
        return organizationService.findOrThrow(request.organizationId());
    }

    /**
     * An organization administrator creates staff and customers for their own
     * organization only. The organization is theirs, never taken from the
     * request; a request naming a different organization is refused rather
     * than silently redirected.
     */
    private static Organization organizationOfOrgAdmin(CreateUserRequest request, User admin) {
        if (!ORG_ADMIN_CREATABLE_ROLES.contains(request.role())) {
            throw new ForbiddenOperationException(
                    "Organization administrators can only create CUSTOMER and SUPPORT_AGENT accounts");
        }
        Organization own = admin.getOrganization();
        if (own == null) {
            throw new BusinessRuleException("Administrator has no organization");
        }
        if (request.organizationId() != null && !Objects.equals(request.organizationId(), own.getId())) {
            throw new ForbiddenOperationException(
                    "Organization administrators can only create users in their own organization");
        }
        return own;
    }

    static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
