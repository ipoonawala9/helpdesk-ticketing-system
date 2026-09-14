package com.ibrahim.helpdesk.user.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.EmailAlreadyInUseException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.service.OrganizationService;
import com.ibrahim.helpdesk.user.dto.CreateUserRequest;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.mapper.UserMapper;
import com.ibrahim.helpdesk.user.repository.UserRepository;
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
     * ORG_ADMIN may view users of their own organization.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id, Long viewerId) {
        User target = findOrThrow(id);
        User viewer = findOrThrow(viewerId);

        boolean allowed = Objects.equals(target.getId(), viewer.getId())
                || viewer.getRole() == UserRole.SUPER_ADMIN
                || (viewer.getRole() == UserRole.ORG_ADMIN && sameOrganization(viewer, target));

        if (!allowed) {
            throw new ForbiddenOperationException(
                    "You can only view your own profile or users of an organization you administer");
        }
        return UserMapper.toResponse(target);
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

    private static boolean sameOrganization(User a, User b) {
        return a.getOrganization() != null
                && b.getOrganization() != null
                && Objects.equals(a.getOrganization().getId(), b.getOrganization().getId());
    }

    static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
