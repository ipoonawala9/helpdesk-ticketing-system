package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the support agent workflow: starting work and resolving,
 * including that refused transitions leave the stored ticket unchanged.
 */
class TicketAgentWorkflowIntegrationTest extends ApiIntegrationTestSupport {

    private long acmeId;
    private long adminId;
    private long agentId;
    private long ticketId;

    @BeforeEach
    void setUp() throws Exception {
        acmeId = createOrganization("Acme Ltd");
        long customerId = createUser("Dana Customer", "CUSTOMER", acmeId);
        adminId = createUser("Alex Admin", "ORG_ADMIN", acmeId);
        agentId = createUser("Sam Agent", "SUPPORT_AGENT", acmeId);
        ticketId = createTicket(customerId);
    }

    @Test
    @DisplayName("full lifecycle OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED is persisted")
    void fullLifecycleToResolved() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());

        startWork(ticketId, agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        LocalDateTime beforeResolve = LocalDateTime.now().minusSeconds(1);
        resolve(ticketId, agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        String stored = fetchTicket(ticketId);
        assertThat(JsonPath.<String>read(stored, "$.status")).isEqualTo("RESOLVED");
        assertThat(LocalDateTime.parse(JsonPath.<String>read(stored, "$.resolvedAt"))).isAfter(beforeResolve);
        assertThat(JsonPath.<Object>read(stored, "$.closedAt")).isNull();
        assertThat(JsonPath.<Integer>read(stored, "$.assignedAgent.id")).isEqualTo((int) agentId);
    }

    @Test
    @DisplayName("resolving before work has started is a 409 and the ticket stays ASSIGNED")
    void cannotResolveBeforeStarting() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());

        resolve(ticketId, agentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot resolve a ticket with status ASSIGNED"));

        assertStored("ASSIGNED");
    }

    @Test
    @DisplayName("starting twice is a 409, not a silent success")
    void cannotStartTwice() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        startWork(ticketId, agentId).andExpect(status().isOk());

        startWork(ticketId, agentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot start work on a ticket with status IN_PROGRESS"));
    }

    @Test
    @DisplayName("resolving twice is a 409 and resolvedAt keeps its original value")
    void cannotResolveTwice() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        startWork(ticketId, agentId).andExpect(status().isOk());
        resolve(ticketId, agentId).andExpect(status().isOk());
        String firstResolvedAt = JsonPath.read(fetchTicket(ticketId), "$.resolvedAt");

        resolve(ticketId, agentId).andExpect(status().isConflict());

        assertThat(JsonPath.<String>read(fetchTicket(ticketId), "$.resolvedAt")).isEqualTo(firstResolvedAt);
    }

    @Test
    @DisplayName("to another agent in the same organization, the ticket does not exist")
    void otherAgentIsForbidden() throws Exception {
        long otherAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);
        assign(ticketId, agentId, adminId).andExpect(status().isOk());

        startWork(ticketId, otherAgentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket with ID " + ticketId + " not found"));
        assertStored("ASSIGNED");

        startWork(ticketId, agentId).andExpect(status().isOk());

        resolve(ticketId, otherAgentId).andExpect(status().isNotFound());
        assertStored("IN_PROGRESS");
    }

    @Test
    @DisplayName("the admin who assigned the ticket cannot work it")
    void adminCannotActAsAgent() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());

        startWork(ticketId, adminId).andExpect(status().isForbidden());

        assertStored("ASSIGNED");
    }

    @Test
    @DisplayName("an agent reassigned away loses the ticket; the new agent can start it")
    void reassignmentMovesOwnership() throws Exception {
        long secondAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        assign(ticketId, secondAgentId, adminId).andExpect(status().isOk());

        startWork(ticketId, agentId).andExpect(status().isNotFound());

        startWork(ticketId, secondAgentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgent.id").value((int) secondAgentId));
    }

    @Test
    @DisplayName("an unassigned OPEN ticket cannot be started by any agent")
    void unassignedTicketCannotBeStarted() throws Exception {
        startWork(ticketId, agentId).andExpect(status().isNotFound());

        assertStored("OPEN");
    }

    @Test
    @DisplayName("an in-progress ticket can be reassigned; it restarts from ASSIGNED and only the new agent owns it")
    void inProgressTicketCanBeReassigned() throws Exception {
        long secondAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        startWork(ticketId, agentId).andExpect(status().isOk());

        assign(ticketId, secondAgentId, adminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value((int) secondAgentId));

        resolve(ticketId, agentId).andExpect(status().isNotFound());
        resolve(ticketId, secondAgentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot resolve a ticket with status ASSIGNED"));

        startWork(ticketId, secondAgentId).andExpect(status().isOk());
        resolve(ticketId, secondAgentId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("resolved and closed tickets still cannot be reassigned")
    void resolvedTicketCannotBeReassigned() throws Exception {
        long secondAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        startWork(ticketId, agentId).andExpect(status().isOk());
        resolve(ticketId, agentId).andExpect(status().isOk());

        assign(ticketId, secondAgentId, adminId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot assign a ticket with status RESOLVED"));
    }

    @Test
    @DisplayName("an unknown ticket is a 404, and a token for a user that does not exist is a 401")
    void unknownIds() throws Exception {
        startWork(999_999L, agentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/tickets/999999/start"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/tickets/{id}/resolve", ticketId)
                        .with(bearer(tokenForUserId(999_999L))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));
    }

    private void assertStored(String expectedStatus) throws Exception {
        String stored = fetchTicket(ticketId);
        assertThat(JsonPath.<String>read(stored, "$.status")).isEqualTo(expectedStatus);
        assertThat(JsonPath.<Object>read(stored, "$.resolvedAt")).isNull();
    }
}
