package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises ticket assignment through the real web, service and persistence
 * layers, including that rejected assignments leave the stored ticket
 * unchanged.
 */
class TicketAssignmentIntegrationTest extends ApiIntegrationTestSupport {

    private long acmeId;
    private long globexId;
    private long acmeAdminId;
    private long acmeAgentId;
    private long ticketId;

    @BeforeEach
    void setUp() throws Exception {
        acmeId = createOrganization("Acme Ltd");
        globexId = createOrganization("Globex Corp");

        long customerId = createUser("Dana Customer", "CUSTOMER", acmeId);
        acmeAdminId = createUser("Alex Admin", "ORG_ADMIN", acmeId);
        acmeAgentId = createUser("Sam Agent", "SUPPORT_AGENT", acmeId);

        ticketId = createTicket(customerId);
    }

    @Test
    @DisplayName("ORG_ADMIN assigns an in-organization SUPPORT_AGENT and the change is persisted")
    void assignsAndPersists() throws Exception {
        assign(ticketId, acmeAgentId, acmeAdminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value((int) acmeAgentId))
                .andExpect(jsonPath("$.assignedAgent.name").value("Sam Agent"));

        mockMvc.perform(get("/api/tickets/{id}", ticketId).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value((int) acmeAgentId));
    }

    @Test
    @DisplayName("an ASSIGNED ticket can be reassigned to another agent before work starts")
    void reassignsBeforeWorkStarts() throws Exception {
        long secondAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);

        assign(ticketId, acmeAgentId, acmeAdminId).andExpect(status().isOk());

        assign(ticketId, secondAgentId, acmeAdminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value((int) secondAgentId));
    }

    @Test
    @DisplayName("an agent from another organization is reported as not found and nothing is stored")
    void rejectsCrossOrganizationAgent() throws Exception {
        long globexAgentId = createUser("Gil Agent", "SUPPORT_AGENT", globexId);

        assign(ticketId, globexAgentId, acmeAdminId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with ID " + globexAgentId + " not found"));

        assertTicketStillOpenAndUnassigned();
    }

    @Test
    @DisplayName("to an admin from another organization the ticket does not exist, and nothing is stored")
    void rejectsCrossOrganizationAdmin() throws Exception {
        long globexAdminId = createUser("Gail Admin", "ORG_ADMIN", globexId);

        assign(ticketId, acmeAgentId, globexAdminId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        assertTicketStillOpenAndUnassigned();
    }

    @Test
    @DisplayName("a non-admin cannot assign, even to a valid agent")
    void rejectsNonAdminActor() throws Exception {
        assign(ticketId, acmeAgentId, acmeAgentId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));

        assertTicketStillOpenAndUnassigned();
    }

    @Test
    @DisplayName("assigning to a user who is not a SUPPORT_AGENT is rejected")
    void rejectsNonAgentTarget() throws Exception {
        assign(ticketId, acmeAdminId, acmeAdminId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Tickets can only be assigned to users with role SUPPORT_AGENT"));

        assertTicketStillOpenAndUnassigned();
    }

    @Test
    @DisplayName("unknown ticket and unknown agent both return 404 in the standard error shape")
    void rejectsUnknownResources() throws Exception {
        assign(999_999L, acmeAgentId, acmeAdminId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/tickets/999999/assign"));

        assign(ticketId, 999_999L, acmeAdminId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with ID 999999 not found"));

        assertTicketStillOpenAndUnassigned();
    }

    private void assertTicketStillOpenAndUnassigned() throws Exception {
        String body = fetchTicket(ticketId);

        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("OPEN");
        assertThat(JsonPath.<Object>read(body, "$.assignedAgent")).isNull();
    }

}
