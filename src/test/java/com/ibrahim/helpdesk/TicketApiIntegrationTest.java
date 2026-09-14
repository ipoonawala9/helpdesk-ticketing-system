package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check across the real web, security, service and persistence
 * layers that no response ever carries a password or Hibernate proxy internals.
 */
class TicketApiIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    @DisplayName("Full create flow exposes no password and no Hibernate proxy fields")
    void createFlowReturnsOnlySafeFields() throws Exception {
        long organizationId = createOrganization("Acme Ltd");

        String userBody = mockMvc.perform(post("/api/users")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dana Customer","email":"%s",
                                 "password":"%s","phoneNumber":"+44 7700 900123",
                                 "role":"CUSTOMER","organizationId":%d}
                                """.formatted(uniqueEmail("Dana Customer"), USER_PASSWORD, organizationId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(userBody).doesNotContain("password", USER_PASSWORD, "hibernateLazyInitializer");
        long customerId = JsonPath.parse(userBody).read("$.id", Integer.class).longValue();

        String ticketBody = mockMvc.perform(post("/api/tickets")
                        .with(as(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Printer will not print",
                                 "description":"It jams on every job",
                                 "category":"HARDWARE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reopenCount").value(0))
                .andExpect(jsonPath("$.ticketNumber").exists())
                .andExpect(jsonPath("$.organization.id").value((int) organizationId))
                .andExpect(jsonPath("$.customer.id").value((int) customerId))
                .andReturn().getResponse().getContentAsString();

        assertThat(ticketBody)
                .doesNotContain("password")
                .doesNotContain(USER_PASSWORD)
                .doesNotContain("hibernateLazyInitializer")
                .doesNotContain("handler");

        long ticketId = JsonPath.parse(ticketBody).read("$.id", Integer.class).longValue();

        // Other integration tests share this database, so find this ticket by id
        // rather than assuming it is first in the list.
        mockMvc.perform(get("/api/tickets").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + ticketId + ")].customer.name").value(contains("Dana Customer")));
    }

    @Test
    @DisplayName("the customer on a new ticket is always the authenticated user; no request field can change it")
    void customerComesFromTheToken() throws Exception {
        long organizationId = createOrganization("Acme Ltd");
        long dana = createUser("Dana Customer", "CUSTOMER", organizationId);
        long kim = createUser("Kim Customer", "CUSTOMER", organizationId);

        mockMvc.perform(post("/api/tickets")
                        .with(as(dana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","description":"d","category":"OTHER","customerId":%d}
                                """.formatted(kim)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.id").value((int) dana));
    }

    @Test
    @DisplayName("creating a ticket needs a token, and only customers may do it")
    void createTicketRequiresCustomer() throws Exception {
        long organizationId = createOrganization("Acme Ltd");
        long agentId = createUser("Sam Agent", "SUPPORT_AGENT", organizationId);
        String body = """
                {"title":"Broken laptop","description":"Screen is cracked","category":"HARDWARE"}
                """;

        mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/tickets"));

        mockMvc.perform(post("/api/tickets").with(as(agentId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("Organization creation validates its body")
    void createOrganizationValidatesBody() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","companyEmail":"not-an-email","domain":"","industry":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.companyEmail")
                        .value("Company email must be a valid email address"));
    }

    @Test
    @DisplayName("a description at the 5000 character validation limit is stored, one over is a 400")
    void descriptionLengthMatchesValidationLimit() throws Exception {
        long customerId = createUser("Dana Customer", "CUSTOMER", createOrganization("Acme Ltd"));

        String atLimit = "x".repeat(5000);
        String body = mockMvc.perform(post("/api/tickets")
                        .with(as(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"%s","category":"OTHER"}
                                """.formatted("t".repeat(200), atLimit)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long ticketId = JsonPath.parse(body).read("$.id", Integer.class).longValue();
        assertThat(JsonPath.<String>read(fetchTicket(ticketId), "$.description")).isEqualTo(atLimit);

        mockMvc.perform(post("/api/tickets")
                        .with(as(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","description":"%s","category":"OTHER"}
                                """.formatted("x".repeat(5001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").value("Description must be at most 5000 characters"));
    }
}
