package com.ibrahim.helpdesk;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for ticket messaging through the real web, service and
 * persistence layers.
 */
class TicketMessagingIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    @Test
    @DisplayName("customer and assigned agent hold a conversation that every participant reads back in order")
    void conversationInOrder() throws Exception {
        postMessage(ticketId, customerId, "Extra detail before anyone picks this up")
                .andExpect(status().isCreated());

        assign(ticketId, agentId, adminId).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(5));
        postMessage(ticketId, agentId, "Hi Dana, which printer model?").andExpect(status().isCreated());
        clock.advance(Duration.ofMinutes(5));
        postMessage(ticketId, customerId, "LaserJet 4000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value((int) ticketId))
                .andExpect(jsonPath("$.sender.id").value((int) customerId))
                .andExpect(jsonPath("$.sender.role").value("CUSTOMER"));

        for (long reader : new long[] {customerId, agentId, adminId}) {
            getMessages(ticketId, reader)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].content").value("Extra detail before anyone picks this up"))
                    .andExpect(jsonPath("$[1].sender.role").value("SUPPORT_AGENT"))
                    .andExpect(jsonPath("$[2].content").value("LaserJet 4000"))
                    .andExpect(content().string(not(containsString("password"))))
                    .andExpect(content().string(not(containsString("hibernateLazyInitializer"))));
        }
    }

    @Test
    @DisplayName("the org admin can read but not post")
    void adminIsReadOnly() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());

        postMessage(ticketId, adminId, "Admin here")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));

        getMessages(ticketId, adminId).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("users outside the conversation cannot read or post, including another organization's admin")
    void outsidersAreForbidden() throws Exception {
        long otherCustomerId = createUser("Kim Customer", "CUSTOMER", acmeId);
        long unassignedAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);
        long globexAdminId = createUser("Gail Admin", "ORG_ADMIN", createOrganization("Globex Corp"));
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        postMessage(ticketId, customerId, "Private detail").andExpect(status().isCreated());

        // Outside their scope the ticket, and so its conversation, does not exist.
        for (long outsider : new long[] {otherCustomerId, unassignedAgentId, globexAdminId}) {
            getMessages(ticketId, outsider).andExpect(status().isNotFound());
        }
        postMessage(ticketId, otherCustomerId, "Let me in").andExpect(status().isNotFound());
        postMessage(ticketId, unassignedAgentId, "Let me in").andExpect(status().isNotFound());
        // Admins may never post, so the role check refuses before any lookup.
        postMessage(ticketId, globexAdminId, "Let me in").andExpect(status().isForbidden());
        getMessages(ticketId, customerId).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("an agent reassigned away loses access to the conversation; the new agent gains it")
    void reassignmentMovesConversationAccess() throws Exception {
        long newAgentId = createUser("Pat Agent", "SUPPORT_AGENT", acmeId);
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        postMessage(ticketId, agentId, "Looking into it").andExpect(status().isCreated());

        assign(ticketId, newAgentId, adminId).andExpect(status().isOk());

        getMessages(ticketId, agentId).andExpect(status().isNotFound());
        postMessage(ticketId, agentId, "Still me").andExpect(status().isNotFound());

        getMessages(ticketId, newAgentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Looking into it"));
        postMessage(ticketId, newAgentId, "Taking over").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a CLOSED ticket's conversation is readable but frozen until the customer reopens")
    void closedTicketIsReadOnlyUntilReopened() throws Exception {
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        startWork(ticketId, agentId).andExpect(status().isOk());
        postMessage(ticketId, agentId, "Replaced the roller").andExpect(status().isCreated());
        resolve(ticketId, agentId).andExpect(status().isOk());
        postMessage(ticketId, customerId, "Thanks, confirming shortly").andExpect(status().isCreated());
        close(ticketId, customerId).andExpect(status().isOk());

        postMessage(ticketId, customerId, "It broke again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot post a message on a CLOSED ticket; reopen it first"));
        getMessages(ticketId, customerId).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        reopen(ticketId, customerId).andExpect(status().isOk());
        postMessage(ticketId, customerId, "It broke again").andExpect(status().isCreated());
        getMessages(ticketId, agentId).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("content is trimmed, and blank or missing fields are rejected")
    void contentValidation() throws Exception {
        postMessage(ticketId, customerId, "  padded  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("padded"));

        postMessage(ticketId, customerId, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").value("Content is required"));
    }

    @Test
    @DisplayName("an unknown ticket is a 404, and reading or posting without a token is a 401")
    void badReferences() throws Exception {
        postMessage(999_999L, customerId, "Hi").andExpect(status().isNotFound());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/tickets/{id}/messages", ticketId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/tickets/{id}/messages", ticketId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deleting a ticket also deletes its conversation")
    void deletingTicketDeletesMessages() throws Exception {
        postMessage(ticketId, customerId, "First").andExpect(status().isCreated());
        postMessage(ticketId, customerId, "Second").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/tickets/{id}", ticketId).with(as(adminId))).andExpect(status().isNoContent());

        getMessages(ticketId, customerId).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reading a thread costs the same number of queries however many senders it has (no N+1)")
    void readingThreadDoesNotQueryPerSender() throws Exception {
        // Without a fetch join Hibernate issues one query per distinct sender it
        // has not already loaded, so each message here comes from a different
        // agent, handed the ticket just before posting.
        assign(ticketId, agentId, adminId).andExpect(status().isOk());
        postMessage(ticketId, agentId, "first agent").andExpect(status().isCreated());
        postMessage(ticketId, customerId, "customer").andExpect(status().isCreated());

        long queriesForTwoSenders = queriesToRead();

        for (int i = 0; i < 4; i++) {
            long nextAgentId = createUser("Agent " + i, "SUPPORT_AGENT", acmeId);
            assign(ticketId, nextAgentId, adminId).andExpect(status().isOk());
            postMessage(ticketId, nextAgentId, "agent " + i).andExpect(status().isCreated());
        }

        long queriesForSixSenders = queriesToRead();

        assertThat(queriesForSixSenders).isEqualTo(queriesForTwoSenders);
    }

    private long queriesToRead() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        getMessages(ticketId, customerId).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }
}
