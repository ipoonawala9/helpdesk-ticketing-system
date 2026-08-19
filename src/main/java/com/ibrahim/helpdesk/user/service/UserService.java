package com.ibrahim.helpdesk.user.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationService organizationService;

    /**
     * Creates a user from a request DTO. The entity is built here rather than
     * bound from the request body, so id and active cannot be set by a caller.
     *
     * <p>The password is currently stored as supplied; hashing arrives with the
     * authentication phase. It is never returned in any response.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {

        Organization organization = resolveOrganization(request);

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(request.password());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role());
        user.setOrganization(organization);
        user.setActive(true);

        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserMapper.toResponse(findOrThrow(id));
    }

    /**
     * Entity-level lookup for other services. Not exposed through a controller.
     */
    @Transactional(readOnly = true)
    public User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private Organization resolveOrganization(CreateUserRequest request) {
        if (request.organizationId() == null) {
            if (request.role() != UserRole.SUPER_ADMIN) {
                throw new BusinessRuleException(
                        "organizationId is required for role " + request.role());
            }
            return null;
        }
        return organizationService.findOrThrow(request.organizationId());
    }
}
