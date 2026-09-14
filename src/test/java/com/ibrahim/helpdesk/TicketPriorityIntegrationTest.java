package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import com.ibrahim.helpdesk.ticket.priority.TicketPriorityBackfill;
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks that priority is always server-determined: set on create,
 * recalculated on edit and on reopen, and never taken from the client.
 */
class TicketPriorityIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketPriorityBackfill backfill;

    private long customerId;
    private long adminId;
    private long agentId;

    @BeforeEach
    void setUp() throws Exception {
        long acmeId = createOrganization("Acme Ltd");
        customerId = createUser("Dana Customer", "CUSTOMER", acmeId);
        adminId = createUser("Alex Admin", "ORG_ADMIN", acmeId);
        agentId = createUser("Sam Agent", "SUPPORT_AGENT", acmeId);
    }

    private ResultActions create(String category, String title, String description, String extraJson)
            throws Exception {
        return mockMvc.perform(post("/api/tickets")
                .with(as(customerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"%s","description":"%s","category":"%s"%s}
                        """.formatted(title, description, category, extraJson)));
    }

    @Test
    @DisplayName("priority is assigned on create from category and wording")
    void assignedOnCreate() throws Exception {
        create("OTHER", "Font question", "How do I change the default font?", "")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priority").value("LOW"));
        create("HARDWARE", "Monitor flicker", "The screen flickers now and then", "")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priority").value("MEDIUM"));
        create("ACCOUNT", "Locked out", "I can't log in since this morning", "")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priority").value("HIGH"));
        create("NETWORK", "Office outage", "Nobody can reach the internet", "")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    @DisplayName("a priority sent by the client is ignored")
    void clientPriorityIgnored() throws Exception {
        create("OTHER", "Font question", "How do I change the default font?", ",\"priority\":\"CRITICAL\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("LOW"));
    }

    @Test
    @DisplayName("editing a ticket recalculates its priority, in either direction")
    void recalculatedOnEdit() throws Exception {
        long ticketId = idOf(create("SOFTWARE", "Toolbar", "An icon looks blurry", "")
                .andExpect(jsonPath("$.priority").value("MEDIUM")));

        mockMvc.perform(put("/api/tickets/{id}", ticketId)
                        .with(as(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Toolbar","description":"Now the whole app crashes on start","category":"SOFTWARE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));

        mockMvc.perform(put("/api/tickets/{id}", ticketId)
                        .with(as(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Toolbar","description":"Fixed itself, just a question now","category":"OTHER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("LOW"));
    }

    @Test
    @DisplayName("each reopen escalates priority, up to HIGH")
    void escalatedOnReopen() throws Exception {
        long ticketId = idOf(create("OTHER", "Font question", "How do I change the default font?", "")
                .andExpect(jsonPath("$.priority").value("LOW")));

        String[] expectedAfterEachReopen = {"MEDIUM", "HIGH", "HIGH"};
        for (String expected : expectedAfterEachReopen) {
            assign(ticketId, agentId, adminId).andExpect(status().isOk());
            startWork(ticketId, agentId).andExpect(status().isOk());
            resolve(ticketId, agentId).andExpect(status().isOk());
            reopen(ticketId, customerId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priority").value(expected));
        }

        assertThat(JsonPath.<Integer>read(fetchTicket(ticketId), "$.reopenCount")).isEqualTo(3);
        assertThat(JsonPath.<String>read(fetchTicket(ticketId), "$.priority")).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("on startup, tickets created before automatic priority get one; existing priorities are untouched")
    void backfillsOnlyMissingPriorities() throws Exception {
        long legacyId = idOf(create("NETWORK", "Office outage", "Nobody can reach the internet", ""));
        long keptId = idOf(create("OTHER", "Font question", "How do I change the default font?", ""));

        // Simulate rows written before this feature: one with no priority, one
        // whose stored priority differs from what the rules would now say.
        Ticket legacy = ticketRepository.findById(legacyId).orElseThrow();
        legacy.setPriority(null);
        ticketRepository.save(legacy);
        Ticket kept = ticketRepository.findById(keptId).orElseThrow();
        kept.setPriority(TicketPriority.HIGH);
        ticketRepository.save(kept);

        backfill.onApplicationReady();

        assertThat(JsonPath.<String>read(fetchTicket(legacyId), "$.priority")).isEqualTo("CRITICAL");
        assertThat(JsonPath.<String>read(fetchTicket(keptId), "$.priority")).isEqualTo("HIGH");
        assertThat(ticketRepository.findByPriorityIsNull()).isEmpty();
    }
}
