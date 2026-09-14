package com.ibrahim.helpdesk.user.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.EmailAlreadyInUseException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.service.OrganizationService;
import com.ibrahim.helpdesk.user.dto.CreateUserRequest;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long SUPER_ADMIN_ID = 100L;
    private static final long ORG_ADMIN_ID = 10L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationService organizationService;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private UserService userService;

    private Organization acme;
    private Organization globex;
    private User superAdmin;
    private User orgAdmin;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, organizationService, passwordEncoder);

        acme = organization(7L, "Acme Ltd");
        globex = organization(8L, "Globex Corp");
        superAdmin = user(SUPER_ADMIN_ID, UserRole.SUPER_ADMIN, null);
        orgAdmin = user(ORG_ADMIN_ID, UserRole.ORG_ADMIN, acme);
    }

    private static Organization organization(long id, String name) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        return organization;
    }

    private static User user(long id, UserRole role, Organization organization) {
        User user = new User();
        user.setId(id);
        user.setName(role + " " + id);
        user.setEmail("user" + id + "@example.test");
        user.setRole(role);
        user.setActive(true);
        user.setOrganization(organization);
        return user;
    }

    private static CreateUserRequest request(UserRole role, Long organizationId) {
        return new CreateUserRequest("Dana Customer", "Dana@Acme.test", "correct-horse",
                "+44 7700 900123", role, organizationId);
    }

    private void givenCreator(User creator) {
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private void stubSave() {
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(3L);
            return u;
        });
    }

    @Nested
    @DisplayName("Created accounts")
    class CreatedAccounts {

        @Test
        @DisplayName("the password is stored only as a bcrypt hash that matches the original")
        void passwordIsHashed() {
            givenCreator(superAdmin);
            when(organizationService.findOrThrow(7L)).thenReturn(acme);
            stubSave();

            userService.createUser(request(UserRole.CUSTOMER, 7L), SUPER_ADMIN_ID);

            String stored = savedUser().getPassword();
            assertThat(stored).startsWith("{bcrypt}").doesNotContain("correct-horse");
            assertThat(passwordEncoder.matches("correct-horse", stored)).isTrue();
        }

        @Test
        @DisplayName("the email is stored trimmed and lower-case, the account is active, and id comes from the database")
        void emailNormalisedAndActive() {
            givenCreator(superAdmin);
            when(organizationService.findOrThrow(7L)).thenReturn(acme);
            stubSave();

            UserResponse response = userService.createUser(new CreateUserRequest(
                    "Dana", "  Dana@Acme.TEST ", "correct-horse", null, UserRole.CUSTOMER, 7L), SUPER_ADMIN_ID);

            User saved = savedUser();
            assertThat(saved.getEmail()).isEqualTo("dana@acme.test");
            assertThat(saved.getActive()).isTrue();
            assertThat(response.id()).isEqualTo(3L);
            assertThat(UserResponse.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("password");
        }

        @Test
        @DisplayName("an email already in use, in any letter case, is refused with 409")
        void duplicateEmailRefused() {
            givenCreator(superAdmin);
            when(organizationService.findOrThrow(7L)).thenReturn(acme);
            when(userRepository.existsByEmailIgnoreCase("dana@acme.test")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request(UserRole.CUSTOMER, 7L), SUPER_ADMIN_ID))
                    .isInstanceOf(EmailAlreadyInUseException.class)
                    .hasMessage("An account with this email already exists");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("SUPER_ADMIN creating users")
    class SuperAdminCreates {

        @ParameterizedTest(name = "can create a {0}")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT", "ORG_ADMIN"})
        void anyOrganizationScopedRole(UserRole role) {
            givenCreator(superAdmin);
            when(organizationService.findOrThrow(8L)).thenReturn(globex);
            stubSave();

            UserResponse response = userService.createUser(request(role, 8L), SUPER_ADMIN_ID);

            assertThat(response.role()).isEqualTo(role);
            assertThat(response.organization().id()).isEqualTo(8L);
        }

        @Test
        @DisplayName("can create another SUPER_ADMIN without an organization")
        void superAdminWithoutOrganization() {
            givenCreator(superAdmin);
            stubSave();

            assertThat(userService.createUser(request(UserRole.SUPER_ADMIN, null), SUPER_ADMIN_ID).organization())
                    .isNull();
        }

        @Test
        @DisplayName("must name an organization for organization-scoped roles")
        void organizationRequired() {
            givenCreator(superAdmin);

            assertThatThrownBy(() -> userService.createUser(request(UserRole.CUSTOMER, null), SUPER_ADMIN_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("organizationId is required");
        }

        @Test
        @DisplayName("an unknown organization is a 404")
        void unknownOrganization() {
            givenCreator(superAdmin);
            when(organizationService.findOrThrow(99L)).thenThrow(new OrganizationNotFoundException(99L));

            assertThatThrownBy(() -> userService.createUser(request(UserRole.CUSTOMER, 99L), SUPER_ADMIN_ID))
                    .isInstanceOf(OrganizationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ORG_ADMIN creating users")
    class OrgAdminCreates {

        @ParameterizedTest(name = "can create a {0} in their own organization without naming it")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT"})
        void staffAndCustomersInOwnOrganization(UserRole role) {
            givenCreator(orgAdmin);
            stubSave();

            UserResponse response = userService.createUser(request(role, null), ORG_ADMIN_ID);

            assertThat(response.organization().id()).isEqualTo(7L);
            verify(organizationService, never()).findOrThrow(any());
        }

        @Test
        @DisplayName("may name their own organization explicitly")
        void ownOrganizationNamed() {
            givenCreator(orgAdmin);
            stubSave();

            assertThat(userService.createUser(request(UserRole.CUSTOMER, 7L), ORG_ADMIN_ID).organization().id())
                    .isEqualTo(7L);
        }

        @ParameterizedTest(name = "cannot create a {0}")
        @EnumSource(value = UserRole.class, names = {"ORG_ADMIN", "SUPER_ADMIN"})
        void cannotCreateAdmins(UserRole role) {
            givenCreator(orgAdmin);

            assertThatThrownBy(() -> userService.createUser(request(role, null), ORG_ADMIN_ID))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Organization administrators can only create CUSTOMER and SUPPORT_AGENT accounts");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("cannot create users in another organization")
        void cannotTargetOtherOrganization() {
            givenCreator(orgAdmin);

            assertThatThrownBy(() -> userService.createUser(request(UserRole.CUSTOMER, 8L), ORG_ADMIN_ID))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Organization administrators can only create users in their own organization");

            verify(userRepository, never()).save(any(User.class));
            verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        }
    }

    @ParameterizedTest(name = "a {0} cannot create users")
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT"})
    void nonAdminsCannotCreateUsers(UserRole role) {
        User creator = user(20L, role, acme);
        givenCreator(creator);

        assertThatThrownBy(() -> userService.createUser(request(UserRole.CUSTOMER, 7L), 20L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only administrators can create users");

        verify(userRepository, never()).save(any(User.class));
    }

    @Nested
    @DisplayName("Viewing and listing users")
    class Viewing {

        private final User target = user(3L, UserRole.CUSTOMER, null);

        @BeforeEach
        void targetInAcme() {
            target.setOrganization(acme);
        }

        @Test
        @DisplayName("a user always sees themselves")
        void self() {
            when(userRepository.findById(3L)).thenReturn(Optional.of(target));

            assertThat(userService.getUserById(3L, 3L).id()).isEqualTo(3L);
        }

        @Test
        @DisplayName("a super admin looks up anyone; an org admin only within their organization")
        void adminScopes() {
            when(userRepository.findById(SUPER_ADMIN_ID)).thenReturn(Optional.of(superAdmin));
            when(userRepository.findById(3L)).thenReturn(Optional.of(target));
            assertThat(userService.getUserById(3L, SUPER_ADMIN_ID).id()).isEqualTo(3L);

            when(userRepository.findById(ORG_ADMIN_ID)).thenReturn(Optional.of(orgAdmin));
            when(userRepository.findByIdAndOrganizationId(3L, 7L)).thenReturn(Optional.of(target));
            assertThat(userService.getUserById(3L, ORG_ADMIN_ID).id()).isEqualTo(3L);
        }

        @Test
        @DisplayName("anyone outside the viewer's scope is reported as not found")
        void outOfScopeIsNotFound() {
            User globexAdmin = user(11L, UserRole.ORG_ADMIN, globex);
            when(userRepository.findById(11L)).thenReturn(Optional.of(globexAdmin));
            when(userRepository.findByIdAndOrganizationId(3L, 8L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.getUserById(3L, 11L))
                    .isInstanceOf(com.ibrahim.helpdesk.exception.UserNotFoundException.class);

            for (UserRole role : new UserRole[] {UserRole.CUSTOMER, UserRole.SUPPORT_AGENT}) {
                User colleague = user(40L + role.ordinal(), role, acme);
                when(userRepository.findById(colleague.getId())).thenReturn(Optional.of(colleague));
                assertThatThrownBy(() -> userService.getUserById(3L, colleague.getId()))
                        .isInstanceOf(com.ibrahim.helpdesk.exception.UserNotFoundException.class);
            }
            verify(userRepository, never()).findById(3L);
        }

        private final org.springframework.data.domain.Pageable firstPage =
                org.springframework.data.domain.PageRequest.of(0, 20);

        private void stubPage() {
            when(userRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(firstPage)))
                    .thenReturn(org.springframework.data.domain.Page.empty(firstPage));
        }

        @Test
        @DisplayName("an org admin may list their own organization, named or not")
        void orgAdminList() {
            when(userRepository.findById(ORG_ADMIN_ID)).thenReturn(Optional.of(orgAdmin));
            stubPage();

            userService.listUsers(ORG_ADMIN_ID, null, null, null, null, firstPage);
            userService.listUsers(ORG_ADMIN_ID, UserRole.SUPPORT_AGENT, 7L, true, "sam", firstPage);

            verify(userRepository, org.mockito.Mockito.times(2))
                    .findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(firstPage));
        }

        @Test
        @DisplayName("an org admin naming another organization gets not found and nothing is queried")
        void orgAdminOtherOrganization() {
            when(userRepository.findById(ORG_ADMIN_ID)).thenReturn(Optional.of(orgAdmin));

            assertThatThrownBy(() -> userService.listUsers(ORG_ADMIN_ID, null, 8L, null, null, firstPage))
                    .isInstanceOf(OrganizationNotFoundException.class);
            verify(userRepository, never())
                    .findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
        }

        @Test
        @DisplayName("a super admin may list any organization")
        void superAdminList() {
            when(userRepository.findById(SUPER_ADMIN_ID)).thenReturn(Optional.of(superAdmin));
            stubPage();

            userService.listUsers(SUPER_ADMIN_ID, null, null, null, null, firstPage);
            userService.listUsers(SUPER_ADMIN_ID, UserRole.ORG_ADMIN, 8L, null, null, firstPage);
        }

        @ParameterizedTest(name = "a {0} cannot list users")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT"})
        void nonAdminsCannotList(UserRole role) {
            User viewer = user(50L, role, acme);
            when(userRepository.findById(50L)).thenReturn(Optional.of(viewer));

            assertThatThrownBy(() -> userService.listUsers(50L, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 20)))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
    }
}
