package com.ibrahim.helpdesk.user.controller;

import com.ibrahim.helpdesk.common.paging.PageResponse;
import com.ibrahim.helpdesk.exception.EmailAlreadyInUseException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.organization.dto.OrganizationSummaryResponse;
import com.ibrahim.helpdesk.security.config.SecurityConfig;
import com.ibrahim.helpdesk.user.dto.CreateUserRequest;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import com.ibrahim.helpdesk.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.ibrahim.helpdesk.support.WebSliceSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    private static final String CREATE_BODY = """
            {"name":"Sam Agent","email":"sam@acme.test","password":"correct-horse",
             "role":"SUPPORT_AGENT","organizationId":7}
            """;

    private static UserResponse sam() {
        return new UserResponse(20L, "Sam Agent", "sam@acme.test", null, UserRole.SUPPORT_AGENT, true,
                new OrganizationSummaryResponse(7L, "Acme Ltd"));
    }

    @Test
    @DisplayName("POST /api/users returns 201, never echoes the password, and passes the creator's id")
    void createUser() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class), eq(10L))).thenReturn(sam());

        mockMvc.perform(post("/api/users").with(as(10L, UserRole.ORG_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).createUser(new CreateUserRequest(
                "Sam Agent", "sam@acme.test", "correct-horse", null, UserRole.SUPPORT_AGENT, 7L), 10L);
    }

    @ParameterizedTest(name = "a {0} cannot create users")
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT"})
    void nonAdminsCannotCreate(UserRole role) throws Exception {
        mockMvc.perform(post("/api/users").with(as(1L, role))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("POST /api/users validates every field")
    void createValidates() throws Exception {
        mockMvc.perform(post("/api/users").with(as(100L, UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"short","phoneNumber":"abc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must be between 8 and 100 characters"))
                .andExpect(jsonPath("$.fieldErrors.role").value("Role is required"))
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("a duplicate email is a 409")
    void duplicateEmail() throws Exception {
        when(userService.createUser(any(), eq(100L))).thenThrow(new EmailAlreadyInUseException());

        mockMvc.perform(post("/api/users").with(as(100L, UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    @DisplayName("GET /api/users binds filters and paging and returns a page")
    void listUsers() throws Exception {
        when(userService.listUsers(eq(10L), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sam()), 0, 10, 1, 1, true, true));

        mockMvc.perform(get("/api/users").with(as(10L, UserRole.ORG_ADMIN))
                        .param("role", "SUPPORT_AGENT").param("active", "true").param("q", "sam")
                        .param("size", "10").param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("sam@acme.test"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(userService).listUsers(10L, UserRole.SUPPORT_AGENT, null, true, "sam",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "email").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @ParameterizedTest(name = "a {0} cannot list users")
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT"})
    void nonAdminsCannotList(UserRole role) throws Exception {
        mockMvc.perform(get("/api/users").with(as(1L, role))).andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("GET /api/users rejects an unknown sort field")
    void listRejectsBadSort() throws Exception {
        mockMvc.perform(get("/api/users").with(as(10L, UserRole.ORG_ADMIN)).param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("sort must be one of")));
    }

    @Test
    @DisplayName("GET /api/users/{id} passes the viewer and maps not found to 404")
    void getUser() throws Exception {
        when(userService.getUserById(20L, 1L)).thenThrow(new UserNotFoundException(20L));

        mockMvc.perform(get("/api/users/20").with(as(1L, UserRole.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with ID 20 not found"));
    }

    @Test
    @DisplayName("every user endpoint needs a token")
    void requiresToken() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/20")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }
}
