package com.ibrahim.helpdesk.organization.controller;

import com.ibrahim.helpdesk.common.paging.PageResponse;
import com.ibrahim.helpdesk.exception.OrganizationNotFoundException;
import com.ibrahim.helpdesk.organization.dto.CreateOrganizationRequest;
import com.ibrahim.helpdesk.organization.dto.OrganizationResponse;
import com.ibrahim.helpdesk.organization.service.OrganizationService;
import com.ibrahim.helpdesk.security.config.SecurityConfig;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
@Import(SecurityConfig.class)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;

    @MockitoBean
    private UserRepository userRepository;

    private static final String CREATE_BODY = """
            {"name":"Acme Ltd","companyEmail":"support@acme.test","domain":"acme.test","industry":"Manufacturing"}
            """;

    private static OrganizationResponse acme() {
        return new OrganizationResponse(7L, "Acme Ltd", "support@acme.test", "acme.test", "Manufacturing");
    }

    @Test
    @DisplayName("a super admin creates an organization")
    void create() throws Exception {
        when(organizationService.createOrganization(any())).thenReturn(acme());

        mockMvc.perform(post("/api/organizations").with(as(100L, UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));

        verify(organizationService).createOrganization(
                new CreateOrganizationRequest("Acme Ltd", "support@acme.test", "acme.test", "Manufacturing"));
    }

    @ParameterizedTest(name = "a {0} cannot create or list organizations")
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT", "ORG_ADMIN"})
    void onlySuperAdmin(UserRole role) throws Exception {
        mockMvc.perform(post("/api/organizations").with(as(1L, role))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizations").with(as(1L, role))).andExpect(status().isForbidden());

        verifyNoInteractions(organizationService);
    }

    @Test
    @DisplayName("GET /api/organizations binds search and paging")
    void list() throws Exception {
        when(organizationService.listOrganizations(any(), any()))
                .thenReturn(new PageResponse<>(List.of(acme()), 1, 5, 6, 2, false, true));

        mockMvc.perform(get("/api/organizations").with(as(100L, UserRole.SUPER_ADMIN))
                        .param("q", "acme").param("page", "1").param("size", "5").param("sort", "industry,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Acme Ltd"))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(organizationService).listOrganizations("acme",
                PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "industry").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @Test
    @DisplayName("GET /api/organizations/{id} passes the viewer and maps not found to 404")
    void getById() throws Exception {
        when(organizationService.getOrganizationById(8L, 1L)).thenThrow(new OrganizationNotFoundException(8L));

        mockMvc.perform(get("/api/organizations/8").with(as(1L, UserRole.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organization with id 8 not found"));
    }

    @Test
    @DisplayName("creation validates the body")
    void validates() throws Exception {
        mockMvc.perform(post("/api/organizations").with(as(100L, UserRole.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","companyEmail":"x","domain":"","industry":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyEmail").value("Company email must be a valid email address"));
        verifyNoInteractions(organizationService);
    }
}
