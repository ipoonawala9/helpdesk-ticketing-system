package com.ibrahim.helpdesk.user.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.service.OrganizationService;
import com.ibrahim.helpdesk.user.dto.CreateUserRequest;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private UserService userService;

    private Organization organization() {
        Organization organization = new Organization();
        organization.setId(7L);
        organization.setName("Acme Ltd");
        return organization;
    }

    @Test
    @DisplayName("createUser activates the user and attaches the resolved organization")
    void createUserAttachesOrganization() {
        when(organizationService.findOrThrow(7L)).thenReturn(organization());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(3L);
            return u;
        });

        UserResponse response = userService.createUser(new CreateUserRequest(
                "Dana Customer", "dana@acme.test", "correct-horse",
                "+44 7700 900123", UserRole.CUSTOMER, 7L));

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.active()).isTrue();
        assertThat(response.organization().id()).isEqualTo(7L);
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("createUser ignores any client attempt to set active or id, and never echoes the password")
    void createUserBuildsEntityFromRequestOnly() {
        when(organizationService.findOrThrow(7L)).thenReturn(organization());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.createUser(new CreateUserRequest(
                "Dana Customer", "dana@acme.test", "correct-horse",
                null, UserRole.CUSTOMER, 7L));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getPassword()).isEqualTo("correct-horse");

        // UserResponse has no password component at all, so it cannot leak.
        assertThat(UserResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("password");
    }

    @Test
    @DisplayName("createUser rejects an organization-scoped role without an organization")
    void createUserRequiresOrganizationForScopedRoles() {
        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest(
                "Dana Customer", "dana@acme.test", "correct-horse",
                null, UserRole.CUSTOMER, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("organizationId is required");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("createUser allows a SUPER_ADMIN with no organization")
    void createUserAllowsSuperAdminWithoutOrganization() {
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.createUser(new CreateUserRequest(
                "Root Admin", "root@helpdesk.test", "correct-horse",
                null, UserRole.SUPER_ADMIN, null));

        assertThat(response.organization()).isNull();
    }

    @Test
    @DisplayName("createUser propagates an unknown organization as a 404-mapped exception")
    void createUserRejectsUnknownOrganization() {
        when(organizationService.findOrThrow(99L))
                .thenThrow(new OrganizationNotFoundException(99L));

        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest(
                "Dana Customer", "dana@acme.test", "correct-horse",
                null, UserRole.CUSTOMER, 99L)))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(userRepository, never()).save(any(User.class));
    }
}
