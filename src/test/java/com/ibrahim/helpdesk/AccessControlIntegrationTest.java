package com.ibrahim.helpdesk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may manage organizations and users, and who may read, edit and delete a
 * ticket, checked end to end with real tokens.
 */
class AccessControlIntegrationTest extends ApiIntegrationTestSupport {

    private long acmeId;
    private long globexId;
    private long acmeAdmin;
    private long acmeAgent;
    private long acmeCustomer;
    private long globexAdmin;

    @BeforeEach
    void setUp() throws Exception {
        acmeId = createOrganization("Acme Ltd");
        globexId = createOrganization("Globex Corp");
        acmeAdmin = createUser("Alex Admin", "ORG_ADMIN", acmeId);
        acmeAgent = createUser("Sam Agent", "SUPPORT_AGENT", acmeId);
        acmeCustomer = createUser("Dana Customer", "CUSTOMER", acmeId);
        globexAdmin = createUser("Gail Admin", "ORG_ADMIN", globexId);
    }

    private ResultActions createUserAs(long creator, String role, Long organizationId, String email) throws Exception {
        return mockMvc.perform(post("/api/users")
                .with(as(creator))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"New User","email":"%s","password":"%s","role":"%s"%s}
                        """.formatted(email, USER_PASSWORD, role,
                        organizationId == null ? "" : ",\"organizationId\":" + organizationId)));
    }

    @Nested
    @DisplayName("Creating users")
    class CreatingUsers {

        @Test
        @DisplayName("an org admin creates agents and customers, always in their own organization, and they can log in")
        void orgAdminCreatesStaffAndCustomers() throws Exception {
            String agentEmail = uniqueEmail("New Agent");
            createUserAs(acmeAdmin, "SUPPORT_AGENT", null, agentEmail)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.organization.id").value((int) acmeId));
            createUserAs(acmeAdmin, "CUSTOMER", acmeId, uniqueEmail("New Customer"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.organization.id").value((int) acmeId));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"%s"}
                                    """.formatted(agentEmail, USER_PASSWORD)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("an org admin cannot create admins or create users in another organization")
        void orgAdminLimits() throws Exception {
            createUserAs(acmeAdmin, "ORG_ADMIN", null, uniqueEmail("x"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Organization administrators can only create CUSTOMER and SUPPORT_AGENT accounts"));
            createUserAs(acmeAdmin, "SUPER_ADMIN", null, uniqueEmail("x")).andExpect(status().isForbidden());
            createUserAs(acmeAdmin, "CUSTOMER", globexId, uniqueEmail("x"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Organization administrators can only create users in their own organization"));
        }

        @Test
        @DisplayName("agents and customers cannot create users at all")
        void nonAdminsCannotCreateUsers() throws Exception {
            createUserAs(acmeAgent, "CUSTOMER", acmeId, uniqueEmail("x")).andExpect(status().isForbidden());
            createUserAs(acmeCustomer, "CUSTOMER", acmeId, uniqueEmail("x")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an email already in use, in any letter case, is a 409")
        void duplicateEmail() throws Exception {
            String email = uniqueEmail("Twin");
            createUserAs(acmeAdmin, "CUSTOMER", null, email).andExpect(status().isCreated());

            createUserAs(acmeAdmin, "CUSTOMER", null, email.toUpperCase())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("An account with this email already exists"));
        }

        @Test
        @DisplayName("a request without a token is a 401")
        void anonymousCannotCreateUsers() throws Exception {
            mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Organizations")
    class Organizations {

        @Test
        @DisplayName("only a super admin can create or list organizations")
        void superAdminOnly() throws Exception {
            mockMvc.perform(get("/api/organizations").with(asSuperAdmin())).andExpect(status().isOk());
            mockMvc.perform(get("/api/organizations").with(as(acmeAdmin))).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/organizations")
                            .with(as(acmeAdmin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Rogue","companyEmail":"a@b.test","domain":"b.test","industry":"x"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("members can view their own organization but not another")
        void membersSeeOwnOrganization() throws Exception {
            mockMvc.perform(get("/api/organizations/{id}", acmeId).with(as(acmeCustomer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Acme Ltd"));
            mockMvc.perform(get("/api/organizations/{id}", acmeId).with(as(globexAdmin)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Organization with id " + acmeId + " not found"));
            mockMvc.perform(get("/api/organizations/{id}", globexId).with(asSuperAdmin()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Viewing users")
    class ViewingUsers {

        @Test
        @DisplayName("self, an admin of the same organization and the super admin may view a user")
        void allowedViewers() throws Exception {
            for (var viewer : new org.springframework.test.web.servlet.request.RequestPostProcessor[] {
                    as(acmeCustomer), as(acmeAdmin), asSuperAdmin()}) {
                mockMvc.perform(get("/api/users/{id}", acmeCustomer).with(viewer))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.password").doesNotExist());
            }
        }

        @Test
        @DisplayName("to colleagues and other organizations' admins the user does not exist")
        void refusedViewers() throws Exception {
            mockMvc.perform(get("/api/users/{id}", acmeCustomer).with(as(acmeAgent))).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/users/{id}", acmeCustomer).with(as(globexAdmin)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("User with ID " + acmeCustomer + " not found"));
        }
    }

    @Nested
    @DisplayName("Tickets")
    class Tickets {

        private long ticketId;

        @BeforeEach
        void openTicket() throws Exception {
            ticketId = createTicket(acmeCustomer);
        }

        @Test
        @DisplayName("the customer, the org admin and the super admin can read it; to outsiders it does not exist")
        void reading() throws Exception {
            long otherCustomer = createUser("Kim Customer", "CUSTOMER", acmeId);

            mockMvc.perform(get("/api/tickets/{id}", ticketId).with(as(acmeCustomer))).andExpect(status().isOk());
            mockMvc.perform(get("/api/tickets/{id}", ticketId).with(as(acmeAdmin))).andExpect(status().isOk());
            mockMvc.perform(get("/api/tickets/{id}", ticketId).with(asSuperAdmin())).andExpect(status().isOk());

            // Indistinguishable from a ticket id that was never created.
            String unknown = mockMvc.perform(get("/api/tickets/{id}", 999_999L).with(as(otherCustomer)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            for (long outsider : new long[] {otherCustomer, acmeAgent, globexAdmin}) {
                String hidden = mockMvc.perform(get("/api/tickets/{id}", ticketId).with(as(outsider)))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").value("Ticket with ID " + ticketId + " not found"))
                        .andReturn().getResponse().getContentAsString();
                org.assertj.core.api.Assertions.assertThat(com.jayway.jsonpath.JsonPath.<String>read(hidden, "$.error"))
                        .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(unknown, "$.error"));
            }
        }

        @Test
        @DisplayName("an agent can read a ticket once it is assigned to them")
        void assignedAgentCanRead() throws Exception {
            assign(ticketId, acmeAgent, acmeAdmin);

            mockMvc.perform(get("/api/tickets/{id}", ticketId).with(as(acmeAgent))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("only the ticket's customer can edit it")
        void editing() throws Exception {
            long otherCustomer = createUser("Kim Customer", "CUSTOMER", acmeId);
            String body = """
                    {"title":"Edited","description":"Edited","category":"OTHER"}
                    """;

            mockMvc.perform(put("/api/tickets/{id}", ticketId).with(as(otherCustomer))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());

            mockMvc.perform(put("/api/tickets/{id}", ticketId).with(as(acmeCustomer))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Edited"));
        }

        @Test
        @DisplayName("only an admin of the ticket's organization can delete it")
        void deleting() throws Exception {
            mockMvc.perform(delete("/api/tickets/{id}", ticketId).with(as(globexAdmin)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/tickets/{id}", ticketId).with(as(acmeCustomer)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/api/tickets/{id}", ticketId).with(as(acmeAdmin)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("every role can list tickets; the scoped lists are tested in TenantIsolationIntegrationTest")
        void listing() throws Exception {
            for (var viewer : new org.springframework.test.web.servlet.request.RequestPostProcessor[] {
                    asSuperAdmin(), as(acmeAdmin), as(acmeAgent), as(acmeCustomer)}) {
                mockMvc.perform(get("/api/tickets").with(viewer)).andExpect(status().isOk());
            }
        }
    }
}
