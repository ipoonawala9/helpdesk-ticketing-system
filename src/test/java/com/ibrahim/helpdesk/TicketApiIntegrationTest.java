package com.ibrahim.helpdesk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check across the real web, service and persistence layers that no
 * response ever carries a password or Hibernate proxy internals.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private long createOrganization() throws Exception {
        String body = mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Ltd","companyEmail":"support@acme.test",
                                 "domain":"acme.test","industry":"Manufacturing"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Ltd"))
                .andReturn().getResponse().getContentAsString();

        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Integer.class).longValue();
    }

    private long createCustomer(long organizationId) throws Exception {
        String body = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dana Customer","email":"dana@acme.test",
                                 "password":"correct-horse","phoneNumber":"+44 7700 900123",
                                 "role":"CUSTOMER","organizationId":%d}
                                """.formatted(organizationId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password", "correct-horse", "hibernateLazyInitializer");

        return com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Integer.class).longValue();
    }

    @Test
    @DisplayName("Full create flow exposes no password and no Hibernate proxy fields")
    void createFlowReturnsOnlySafeFields() throws Exception {
        long organizationId = createOrganization();
        long customerId = createCustomer(organizationId);

        String ticketBody = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Printer will not print",
                                 "description":"It jams on every job",
                                 "category":"HARDWARE","customerId":%d}
                                """.formatted(customerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reopenCount").value(0))
                .andExpect(jsonPath("$.ticketNumber").exists())
                .andExpect(jsonPath("$.organization.id").value((int) organizationId))
                .andExpect(jsonPath("$.customer.id").value((int) customerId))
                .andReturn().getResponse().getContentAsString();

        assertThat(ticketBody)
                .doesNotContain("password")
                .doesNotContain("correct-horse")
                .doesNotContain("hibernateLazyInitializer")
                .doesNotContain("handler");

        long ticketId = com.jayway.jsonpath.JsonPath.parse(ticketBody).read("$.id", Integer.class).longValue();

        // Other integration tests share this database, so find this ticket by id
        // rather than assuming it is first in the list.
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + ticketId + ")].customer.name")
                        .value(org.hamcrest.Matchers.contains("Dana Customer")));
    }

    @Test
    @DisplayName("A ticket for an unknown customer is a 404 in the standard error shape")
    void createTicketForUnknownCustomerReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Broken laptop","description":"Screen is cracked",
                                 "category":"HARDWARE","customerId":999999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/tickets"));
    }

    @Test
    @DisplayName("Organization creation validates its body")
    void createOrganizationValidatesBody() throws Exception {
        mockMvc.perform(post("/api/organizations")
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
        long organizationId = createOrganization();
        long customerId = createCustomer(organizationId);

        String atLimit = "x".repeat(5000);
        String body = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"%s","category":"OTHER","customerId":%d}
                                """.formatted("t".repeat(200), atLimit, customerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long ticketId = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Integer.class).longValue();
        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(jsonPath("$.description").value(atLimit));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","description":"%s","category":"OTHER","customerId":%d}
                                """.formatted("x".repeat(5001), customerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").value("Description must be at most 5000 characters"));
    }
}
