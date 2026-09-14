package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup and request helpers for end-to-end tests that drive the API
 * through the real web, service and persistence layers. All data goes in
 * through the public endpoints, and emails are randomised so test classes can
 * share one database without colliding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiIntegrationTestSupport.TestClockConfig.class)
abstract class ApiIntegrationTestSupport {

    @TestConfiguration
    static class TestClockConfig {

        /** Replaces the system clock so tests can move time forward. */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(ZoneId.systemDefault());
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        // The clock bean is shared by every test in the context.
        clock.reset();
    }

    protected long createOrganization(String name) throws Exception {
        String slug = name.toLowerCase().replace(' ', '-');
        return idOf(mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","companyEmail":"support@%s.test",
                         "domain":"%s.test","industry":"Technology"}
                        """.formatted(name, slug, slug))));
    }

    protected long createUser(String name, String role, long organizationId) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@example.test";
        return idOf(mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","email":"%s","password":"correct-horse",
                         "role":"%s","organizationId":%d}
                        """.formatted(name, email, role, organizationId))));
    }

    protected long createTicket(long customerId) throws Exception {
        return idOf(mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Printer will not print","description":"It jams on every job",
                         "category":"HARDWARE","customerId":%d}
                        """.formatted(customerId))));
    }

    protected ResultActions assign(long ticketId, long agentId, long adminId) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/assign", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"agentId":%d,"adminId":%d}
                        """.formatted(agentId, adminId)));
    }

    protected ResultActions startWork(long ticketId, long agentId) throws Exception {
        return agentAction("start", ticketId, agentId);
    }

    protected ResultActions resolve(long ticketId, long agentId) throws Exception {
        return agentAction("resolve", ticketId, agentId);
    }

    protected ResultActions reopen(long ticketId, long customerId) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/reopen", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":%d}
                        """.formatted(customerId)));
    }

    protected ResultActions close(long ticketId, long userId) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/close", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d}
                        """.formatted(userId)));
    }

    protected String fetchTicket(long ticketId) throws Exception {
        return mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions agentAction(String action, long ticketId, long agentId) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/" + action, ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"agentId":%d}
                        """.formatted(agentId)));
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Integer.class).longValue();
    }
}
