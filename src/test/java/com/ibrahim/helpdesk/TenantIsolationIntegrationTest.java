package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multi-tenant isolation end to end: what each role's lists contain, that
 * nothing crosses organizations, and that no ordinary API moves data between
 * organizations.
 *
 * <p>Two organizations are set up per test:
 * <pre>
 * Acme    customer A1: ticket t1 (assigned to agent X), ticket t2 (unassigned)
 *         customer A2: ticket t3 (assigned to agent Y)
 * Globex  customer G1: ticket t4 (assigned to agent Z)
 * </pre>
 */
class TenantIsolationIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long acme;
    private long globex;
    private long acmeAdmin;
    private long globexAdmin;
    private long customerA1;
    private long customerA2;
    private long customerG1;
    private long agentX;
    private long agentY;
    private long agentZ;
    private long t1;
    private long t2;
    private long t3;
    private long t4;

    @BeforeEach
    void setUp() throws Exception {
        acme = createOrganization("Acme Ltd");
        globex = createOrganization("Globex Corp");
        acmeAdmin = createUser("Alex Admin", "ORG_ADMIN", acme);
        globexAdmin = createUser("Gail Admin", "ORG_ADMIN", globex);
        customerA1 = createUser("Ann Customer", "CUSTOMER", acme);
        customerA2 = createUser("Ben Customer", "CUSTOMER", acme);
        customerG1 = createUser("Gus Customer", "CUSTOMER", globex);
        agentX = createUser("Xia Agent", "SUPPORT_AGENT", acme);
        agentY = createUser("Yan Agent", "SUPPORT_AGENT", acme);
        agentZ = createUser("Zed Agent", "SUPPORT_AGENT", globex);

        t1 = createTicket(customerA1);
        t2 = createTicket(customerA1);
        t3 = createTicket(customerA2);
        t4 = createTicket(customerG1);

        assign(t1, agentX, acmeAdmin).andExpect(status().isOk());
        assign(t3, agentY, acmeAdmin).andExpect(status().isOk());
        assign(t4, agentZ, globexAdmin).andExpect(status().isOk());
    }

    private List<Integer> ticketIdsVisibleTo(RequestPostProcessor viewer) throws Exception {
        String body = mockMvc.perform(get("/api/tickets").with(viewer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].id");
    }

    private static List<Integer> ids(long... ids) {
        return java.util.Arrays.stream(ids).mapToObj(id -> (int) id).toList();
    }

    @Nested
    @DisplayName("Ticket lists")
    class TicketLists {

        @Test
        @DisplayName("a customer sees exactly their own tickets, newest first")
        void customer() throws Exception {
            assertThat(ticketIdsVisibleTo(as(customerA1))).containsExactlyElementsOf(ids(t2, t1));
            assertThat(ticketIdsVisibleTo(as(customerA2))).containsExactlyElementsOf(ids(t3));
            assertThat(ticketIdsVisibleTo(as(customerG1))).containsExactlyElementsOf(ids(t4));
        }

        @Test
        @DisplayName("an agent sees exactly the tickets assigned to them, not the rest of the organization")
        void agent() throws Exception {
            assertThat(ticketIdsVisibleTo(as(agentX))).containsExactlyElementsOf(ids(t1));
            assertThat(ticketIdsVisibleTo(as(agentY))).containsExactlyElementsOf(ids(t3));
        }

        @Test
        @DisplayName("an org admin sees every ticket of their organization and none of another's")
        void orgAdmin() throws Exception {
            assertThat(ticketIdsVisibleTo(as(acmeAdmin))).containsExactlyElementsOf(ids(t3, t2, t1));
            assertThat(ticketIdsVisibleTo(as(globexAdmin))).containsExactlyElementsOf(ids(t4));
        }

        @Test
        @DisplayName("the super admin sees tickets of every organization")
        void superAdmin() throws Exception {
            assertThat(ticketIdsVisibleTo(asSuperAdmin())).containsAll(ids(t1, t2, t3, t4));
        }

        @Test
        @DisplayName("an agent's list follows reassignment")
        void followsReassignment() throws Exception {
            assign(t1, agentY, acmeAdmin).andExpect(status().isOk());

            assertThat(ticketIdsVisibleTo(as(agentX))).isEmpty();
            assertThat(ticketIdsVisibleTo(as(agentY))).containsExactlyElementsOf(ids(t3, t1));
        }

        @Test
        @DisplayName("listing costs the same number of queries however many customers and agents are involved (no N+1)")
        void noQueryPerTicket() throws Exception {
            long queriesForThreeTickets = queriesToList(as(acmeAdmin));

            for (int i = 0; i < 4; i++) {
                long customer = createUser("Extra Customer " + i, "CUSTOMER", acme);
                long agent = createUser("Extra Agent " + i, "SUPPORT_AGENT", acme);
                assign(createTicket(customer), agent, acmeAdmin).andExpect(status().isOk());
            }

            assertThat(ticketIdsVisibleTo(as(acmeAdmin))).hasSize(7);
            assertThat(queriesToList(as(acmeAdmin))).isEqualTo(queriesForThreeTickets);
        }

        private long queriesToList(RequestPostProcessor viewer) throws Exception {
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.clear();
            mockMvc.perform(get("/api/tickets").with(viewer)).andExpect(status().isOk());
            return statistics.getPrepareStatementCount();
        }
    }

    @Nested
    @DisplayName("Cross-organization access is indistinguishable from a missing record")
    class CrossOrganization {

        @Test
        @DisplayName("another organization's ticket, its messages and its workflow actions are all 404")
        void ticketsOfAnotherOrganization() throws Exception {
            mockMvc.perform(get("/api/tickets/{id}", t4).with(as(acmeAdmin))).andExpect(status().isNotFound());
            getMessages(t4, acmeAdmin).andExpect(status().isNotFound());
            assign(t4, agentX, acmeAdmin).andExpect(status().isNotFound());
            close(t4, acmeAdmin).andExpect(status().isNotFound());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/tickets/{id}", t4).with(as(acmeAdmin))).andExpect(status().isNotFound());

            assertThat(JsonPath.<String>read(fetchTicket(t4), "$.status")).isEqualTo("ASSIGNED");
        }

        @Test
        @DisplayName("an agent of another organization cannot be assigned, and is reported as not found")
        void agentOfAnotherOrganization() throws Exception {
            assign(t2, agentZ, acmeAdmin)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("User with ID " + agentZ + " not found"));
        }

        @Test
        @DisplayName("another organization's users and the organization itself are 404")
        void usersAndOrganizations() throws Exception {
            mockMvc.perform(get("/api/users/{id}", customerG1).with(as(acmeAdmin))).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/organizations/{id}", globex).with(as(acmeAdmin))).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("User lists")
    class UserLists {

        private List<Integer> userIds(RequestPostProcessor viewer, String query) throws Exception {
            String body = mockMvc.perform(get("/api/users" + query).with(viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].password").isEmpty())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(body, "$[*].id");
        }

        @Test
        @DisplayName("an org admin lists only their organization's users, and can narrow to agents for assignment")
        void orgAdmin() throws Exception {
            assertThat(userIds(as(acmeAdmin), ""))
                    .containsExactlyInAnyOrderElementsOf(ids(acmeAdmin, customerA1, customerA2, agentX, agentY));
            assertThat(userIds(as(acmeAdmin), "?role=SUPPORT_AGENT"))
                    .containsExactlyInAnyOrderElementsOf(ids(agentX, agentY));
            assertThat(userIds(as(acmeAdmin), "?organizationId=" + acme)).hasSize(5);
        }

        @Test
        @DisplayName("an org admin asking for another organization's users gets 404")
        void orgAdminOtherOrganization() throws Exception {
            mockMvc.perform(get("/api/users").param("organizationId", String.valueOf(globex)).with(as(acmeAdmin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("the super admin lists any organization's users")
        void superAdmin() throws Exception {
            assertThat(userIds(asSuperAdmin(), "?organizationId=" + globex))
                    .containsExactlyInAnyOrderElementsOf(ids(globexAdmin, customerG1, agentZ));
            assertThat(userIds(asSuperAdmin(), "?role=ORG_ADMIN")).containsAll(ids(acmeAdmin, globexAdmin));
        }

        @Test
        @DisplayName("agents and customers cannot list users")
        void nonAdmins() throws Exception {
            mockMvc.perform(get("/api/users").with(as(agentX))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users").with(as(customerA1))).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("No ordinary API moves data between organizations")
    class NoOrganizationChanges {

        @Test
        @DisplayName("an organization or customer id sent when creating a ticket is ignored")
        void createIgnoresOrganization() throws Exception {
            mockMvc.perform(post("/api/tickets")
                            .with(as(customerA1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"t","description":"d","category":"OTHER",
                                     "organizationId":%d,"customerId":%d}
                                    """.formatted(globex, customerG1)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.organization.id").value((int) acme))
                    .andExpect(jsonPath("$.customer.id").value((int) customerA1));
        }

        @Test
        @DisplayName("an organization, customer or agent sent when editing a ticket is ignored")
        void editIgnoresOrganization() throws Exception {
            mockMvc.perform(put("/api/tickets/{id}", t1)
                            .with(as(customerA1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"Edited","description":"Edited","category":"OTHER",
                                     "organizationId":%d,"customerId":%d,"assignedAgentId":%d,"status":"CLOSED"}
                                    """.formatted(globex, customerG1, agentZ)))
                    .andExpect(status().isOk());

            String stored = fetchTicket(t1);
            assertThat(JsonPath.<Integer>read(stored, "$.organization.id")).isEqualTo((int) acme);
            assertThat(JsonPath.<Integer>read(stored, "$.customer.id")).isEqualTo((int) customerA1);
            assertThat(JsonPath.<Integer>read(stored, "$.assignedAgent.id")).isEqualTo((int) agentX);
            assertThat(JsonPath.<String>read(stored, "$.status")).isEqualTo("ASSIGNED");
        }

        @Test
        @DisplayName("there is no endpoint to change a user, so a user's organization cannot be changed")
        void usersCannotBeModified() throws Exception {
            String body = """
                    {"organizationId":%d,"role":"SUPER_ADMIN"}
                    """.formatted(globex);
            mockMvc.perform(put("/api/users/{id}", agentX).with(asSuperAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isMethodNotAllowed());
            mockMvc.perform(patch("/api/users/{id}", agentX).with(asSuperAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isMethodNotAllowed());

            mockMvc.perform(get("/api/users/{id}", agentX).with(asSuperAdmin()))
                    .andExpect(jsonPath("$.organization.id").value((int) acme))
                    .andExpect(jsonPath("$.role").value("SUPPORT_AGENT"));
        }
    }

    @Nested
    @DisplayName("Super admin")
    class SuperAdmin {

        @Test
        @DisplayName("can read any ticket, but cannot act on tickets or read their conversations")
        void readOnlyOnTickets() throws Exception {
            mockMvc.perform(get("/api/tickets/{id}", t4).with(asSuperAdmin())).andExpect(status().isOk());

            long superAdmin = superAdminId();
            assign(t2, agentX, superAdmin).andExpect(status().isForbidden());
            close(t4, superAdmin).andExpect(status().isForbidden());
            getMessages(t4, superAdmin).andExpect(status().isForbidden());
        }
    }
}
