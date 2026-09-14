package com.ibrahim.helpdesk;

import com.ibrahim.helpdesk.security.jwt.JwtTokenService;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup and request helpers for end-to-end tests that drive the API
 * through the real web, security, service and persistence layers.
 *
 * <p>Every request is authenticated with a real signed access token for the
 * user acting in it, so helpers take that user's id, e.g.
 * {@code startWork(ticketId, agentId)} is sent as that agent. Tokens are minted
 * directly rather than through the login endpoint to keep tests fast; login
 * itself is covered by {@link AuthIntegrationTest}.
 *
 * <p>Organizations and users are created as the bootstrap super admin
 * configured in the test properties. Emails are randomised so test classes can
 * share one database without colliding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiIntegrationTestSupport.TestClockConfig.class)
abstract class ApiIntegrationTestSupport {

    protected static final String SUPER_ADMIN_EMAIL = "superadmin@helpdesk.test";
    protected static final String SUPER_ADMIN_PASSWORD = "super-admin-test-password";
    protected static final String USER_PASSWORD = "correct-horse";

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

    @Autowired
    protected JwtTokenService tokenService;

    @Autowired
    protected UserRepository userRepository;

    @BeforeEach
    void resetClock() {
        // The clock bean is shared by every test in the context.
        clock.reset();
    }

    // ----- authentication -------------------------------------------------

    /** A valid access token for an existing user. */
    protected String tokenFor(long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return tokenService.issue(user).value();
    }

    /** Sends the request as the given user. */
    protected RequestPostProcessor as(long userId) {
        return bearer(tokenFor(userId));
    }

    protected RequestPostProcessor asSuperAdmin() {
        return as(superAdminId());
    }

    protected static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    protected long superAdminId() {
        return userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).orElseThrow().getId();
    }

    /**
     * A correctly signed token for a user id that may not exist, to test how
     * tokens for deleted accounts are treated.
     */
    protected String tokenForUserId(long userId) {
        User ghost = new User();
        ghost.setId(userId);
        ghost.setRole(UserRole.CUSTOMER);
        return tokenService.issue(ghost).value();
    }

    // ----- setup through the public API -------------------------------------

    protected long createOrganization(String name) throws Exception {
        String slug = name.toLowerCase().replace(' ', '-');
        return idOf(mockMvc.perform(post("/api/organizations")
                .with(asSuperAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","companyEmail":"support@%s.test",
                         "domain":"%s.test","industry":"Technology"}
                        """.formatted(name, slug, slug))));
    }

    protected long createUser(String name, String role, long organizationId) throws Exception {
        return idOf(mockMvc.perform(post("/api/users")
                .with(asSuperAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","email":"%s","password":"%s",
                         "role":"%s","organizationId":%d}
                        """.formatted(name, uniqueEmail(name), USER_PASSWORD, role, organizationId))));
    }

    protected static String uniqueEmail(String name) {
        return name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@example.test";
    }

    protected long createTicket(long customerId) throws Exception {
        return idOf(mockMvc.perform(post("/api/tickets")
                .with(as(customerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Printer will not print","description":"It jams on every job",
                         "category":"HARDWARE"}
                        """)));
    }

    // ----- ticket workflow and messages, each sent as the acting user -------

    protected ResultActions assign(long ticketId, long agentId, long adminId) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/assign", ticketId)
                .with(as(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"agentId":%d}
                        """.formatted(agentId)));
    }

    protected ResultActions startWork(long ticketId, long agentId) throws Exception {
        return action("start", ticketId, agentId);
    }

    protected ResultActions resolve(long ticketId, long agentId) throws Exception {
        return action("resolve", ticketId, agentId);
    }

    protected ResultActions reopen(long ticketId, long customerId) throws Exception {
        return action("reopen", ticketId, customerId);
    }

    protected ResultActions close(long ticketId, long userId) throws Exception {
        return action("close", ticketId, userId);
    }

    protected ResultActions postMessage(long ticketId, long senderId, String content) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/messages", ticketId)
                .with(as(senderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"content":"%s"}
                        """.formatted(content)));
    }

    protected ResultActions getMessages(long ticketId, long userId) throws Exception {
        return mockMvc.perform(get("/api/tickets/{id}/messages", ticketId).with(as(userId)));
    }

    /** Reads a ticket as the super admin, who can see every ticket. */
    protected String fetchTicket(long ticketId) throws Exception {
        return mockMvc.perform(get("/api/tickets/{id}", ticketId).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions action(String action, long ticketId, long userId) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/" + action, ticketId).with(as(userId)));
    }

    protected long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Integer.class).longValue();
    }
}
