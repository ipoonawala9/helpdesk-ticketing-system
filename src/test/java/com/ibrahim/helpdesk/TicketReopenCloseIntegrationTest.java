package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for closing and reopening tickets, including the
 * time-based rules, which are driven by moving the shared test clock forward.
 */
class TicketReopenCloseIntegrationTest extends ApiIntegrationTestSupport {

    private long acmeId;
    private long customerId;
    private long adminId;
    private long agentId;
    private long ticketId;

    @BeforeEach
    void setUp() throws Exception {
        acmeId = createOrganization("Acme Ltd");
        customerId = createUser("Dana Customer", "CUSTOMER", acmeId);
        adminId = createUser("Alex Admin", "ORG_ADMIN", acmeId);
        agentId = createUser("Sam Agent", "SUPPORT_AGENT", acmeId);
        ticketId = createTicket(customerId);
    }

    private void resolveTicket() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        startWork(ticketId, agentId).andExpect(status().isOk());
        resolve(ticketId, agentId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("customer closes, reopens, the same agent resolves again, and the customer closes again")
    void fullCloseReopenCycle() throws Exception {
        resolveTicket();

        close(ticketId, customerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.resolvedAt").exists());

        reopen(ticketId, customerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.assignedAgent.id").value((int) agentId))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        startWork(ticketId, agentId).andExpect(status().isOk());
        resolve(ticketId, agentId).andExpect(status().isOk());
        close(ticketId, customerId).andExpect(status().isOk());

        String stored = fetchTicket(ticketId);
        assertThat(JsonPath.<String>read(stored, "$.status")).isEqualTo("CLOSED");
        assertThat(JsonPath.<Integer>read(stored, "$.reopenCount")).isEqualTo(1);
        assertThat(JsonPath.<String>read(stored, "$.closedAt")).isNotNull();
    }

    @Test
    @DisplayName("a RESOLVED ticket the customer never closed can be reopened and restarted by its agent")
    void reopenResolvedWithoutClosing() throws Exception {
        resolveTicket();

        reopen(ticketId, customerId).andExpect(status().isOk());

        startWork(ticketId, agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("an admin cannot close until 3 hours after resolution, then can")
    void adminCloseWaitsForCustomerWindow() throws Exception {
        resolveTicket();

        close(ticketId, adminId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith(
                        "The customer has 3 hours after resolution to close this ticket")));
        assertThat(JsonPath.<String>read(fetchTicket(ticketId), "$.status")).isEqualTo("RESOLVED");

        clock.advance(Duration.ofHours(2).plusMinutes(59));
        close(ticketId, adminId).andExpect(status().isConflict());

        clock.advance(Duration.ofMinutes(2));
        close(ticketId, adminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("a closed ticket can be reopened within 7 days but not after")
    void reopenWindowExpires() throws Exception {
        resolveTicket();
        close(ticketId, customerId).andExpect(status().isOk());

        clock.advance(Duration.ofDays(7).plusMinutes(1));

        reopen(ticketId, customerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Tickets can only be reopened within 7 days of being closed; please open a new ticket"));
        assertThat(JsonPath.<String>read(fetchTicket(ticketId), "$.status")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("an admin can hand a reopened ticket to a different agent, who then owns it")
    void adminReassignsReopenedTicket() throws Exception {
        long newAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);
        resolveTicket();
        reopen(ticketId, customerId).andExpect(status().isOk());

        assign(ticketId, newAgentId, adminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedAgent.id").value((int) newAgentId));

        startWork(ticketId, agentId).andExpect(status().isNotFound());
        startWork(ticketId, newAgentId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("only the ticket's customer can reopen, and the agent cannot close")
    void otherUsersAreForbidden() throws Exception {
        long otherCustomerId = createUser("Kim Customer", "CUSTOMER", acmeId);
        resolveTicket();

        // Another customer cannot see the ticket; agents may never reopen or close.
        reopen(ticketId, otherCustomerId).andExpect(status().isNotFound());
        reopen(ticketId, agentId).andExpect(status().isForbidden());
        close(ticketId, otherCustomerId).andExpect(status().isNotFound());
        close(ticketId, agentId).andExpect(status().isForbidden());

        assertThat(JsonPath.<String>read(fetchTicket(ticketId), "$.status")).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("an admin from another organization cannot close, even after the window")
    void crossOrganizationAdminCannotClose() throws Exception {
        long globexId = createOrganization("Globex Corp");
        long globexAdminId = createUser("Gail Admin", "ORG_ADMIN", globexId);
        resolveTicket();
        clock.advance(Duration.ofDays(1));

        close(ticketId, globexAdminId).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tickets that are not RESOLVED cannot be closed, and open ones cannot be reopened")
    void wrongStatusesAreConflicts() throws Exception {
        close(ticketId, customerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot close a ticket with status OPEN"));
        reopen(ticketId, customerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot reopen a ticket with status OPEN"));

        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        startWork(ticketId, agentId).andExpect(status().isOk());
        close(ticketId, customerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot close a ticket with status IN_PROGRESS"));
    }
}
