package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pagination, filtering, sorting and search on the ticket, user and
 * organization lists, run against a real database so query behaviour such as
 * LIKE escaping and ordering is what the database actually does.
 *
 * <p>Each test works inside its own fresh organization, so rows written by
 * other test classes sharing the database never appear in the results.
 */
class TicketSearchIntegrationTest extends ApiIntegrationTestSupport {

    private long org;
    private long admin;
    private long customer;
    private long agent;

    @BeforeEach
    void setUp() throws Exception {
        org = createOrganization("Search Org");
        admin = createUser("Search Admin", "ORG_ADMIN", org);
        customer = createUser("Search Customer", "CUSTOMER", org);
        agent = createUser("Search Agent", "SUPPORT_AGENT", org);
    }

    private long ticket(String category, String title, String description) throws Exception {
        return idOf(mockMvc.perform(post("/api/tickets")
                .with(as(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"%s","description":"%s","category":"%s"}
                        """.formatted(title, description, category))));
    }

    private String list(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<Integer> ids(String body) {
        return JsonPath.read(body, "$.content[*].id");
    }

    private static List<Integer> ids(long... ids) {
        return java.util.Arrays.stream(ids).mapToObj(id -> (int) id).toList();
    }

    @Test
    @DisplayName("pages walk the whole list without repeating or skipping a ticket")
    void pagination() throws Exception {
        long[] created = new long[5];
        for (int i = 0; i < 5; i++) {
            created[i] = ticket("OTHER", "Ticket " + i, "Same content");
        }

        String first = list(get("/api/tickets").param("size", "2"));
        assertThat(JsonPath.<Integer>read(first, "$.totalElements")).isEqualTo(5);
        assertThat(JsonPath.<Integer>read(first, "$.totalPages")).isEqualTo(3);
        assertThat(JsonPath.<Boolean>read(first, "$.first")).isTrue();
        assertThat(JsonPath.<Boolean>read(first, "$.last")).isFalse();

        java.util.List<Integer> seen = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = list(get("/api/tickets").param("size", "2").param("page", String.valueOf(page)));
            seen.addAll(ids(body));
            assertThat(JsonPath.<Boolean>read(body, "$.last")).isEqualTo(page == 2);
        }

        // Newest first, and every ticket exactly once.
        assertThat(seen).containsExactlyElementsOf(ids(created[4], created[3], created[2], created[1], created[0]));
        assertThat(ids(list(get("/api/tickets").param("size", "2").param("page", "3")))).isEmpty();
    }

    @Test
    @DisplayName("sorting by priority follows severity, not the alphabetical order of the names")
    void prioritySortBySeverity() throws Exception {
        long low = ticket("OTHER", "Font question", "How do I change the font");
        long critical = ticket("NETWORK", "Office outage", "Nobody can connect");
        long medium = ticket("HARDWARE", "Monitor flicker", "Flickers now and then");
        long high = ticket("ACCOUNT", "Locked out", "I am locked out of my account");

        assertThat(ids(list(get("/api/tickets").param("sort", "priority,desc"))))
                .containsExactlyElementsOf(ids(critical, high, medium, low));
        assertThat(ids(list(get("/api/tickets").param("sort", "priority,asc"))))
                .containsExactlyElementsOf(ids(low, medium, high, critical));
    }

    @Test
    @DisplayName("sorting by status follows the lifecycle")
    void statusSortByLifecycle() throws Exception {
        long open = ticket("OTHER", "a", "a");
        long inProgress = ticket("OTHER", "b", "b");
        long assigned = ticket("OTHER", "c", "c");
        assign(inProgress, agent, admin).andExpect(status().isOk());
        startWork(inProgress, agent).andExpect(status().isOk());
        assign(assigned, agent, admin).andExpect(status().isOk());

        assertThat(ids(list(get("/api/tickets").param("sort", "status"))))
                .containsExactlyElementsOf(ids(open, assigned, inProgress));
    }

    @Test
    @DisplayName("filters combine: several statuses, a category, and unassigned")
    void filters() throws Exception {
        long openHardware = ticket("HARDWARE", "Printer", "Jams");
        long openSoftware = ticket("SOFTWARE", "App", "Slow");
        long assignedHardware = ticket("HARDWARE", "Scanner", "Dead");
        assign(assignedHardware, agent, admin).andExpect(status().isOk());

        assertThat(ids(list(get("/api/tickets").param("category", "HARDWARE"))))
                .containsExactlyInAnyOrderElementsOf(ids(openHardware, assignedHardware));
        assertThat(ids(list(get("/api/tickets").param("unassigned", "true"))))
                .containsExactlyInAnyOrderElementsOf(ids(openHardware, openSoftware));
        assertThat(ids(list(get("/api/tickets").param("status", "OPEN", "ASSIGNED").param("category", "HARDWARE"))))
                .containsExactlyInAnyOrderElementsOf(ids(openHardware, assignedHardware));
        assertThat(ids(list(get("/api/tickets").param("assignedAgentId", String.valueOf(agent)))))
                .containsExactlyElementsOf(ids(assignedHardware));
        assertThat(ids(list(get("/api/tickets").param("customerId", String.valueOf(customer))))).hasSize(3);
    }

    @Test
    @DisplayName("search matches ticket number, title or description, ignoring case")
    void search() throws Exception {
        long byTitle = ticket("OTHER", "Printer on floor 3", "Paper jam");
        long byDescription = ticket("OTHER", "Hardware issue", "The PRINTER smokes");
        long unrelated = ticket("OTHER", "Laptop", "Battery drains");
        String number = JsonPath.read(fetchTicket(unrelated), "$.ticketNumber");

        assertThat(ids(list(get("/api/tickets").param("q", "printer"))))
                .containsExactlyInAnyOrderElementsOf(ids(byTitle, byDescription));
        assertThat(ids(list(get("/api/tickets").param("q", number.toLowerCase()))))
                .containsExactlyElementsOf(ids(unrelated));
    }

    @Test
    @DisplayName("% and _ in a search are matched literally, not as wildcards")
    void searchEscapesWildcards() throws Exception {
        long percent = ticket("BILLING", "Refund 50% of invoice", "Half refund");
        ticket("BILLING", "Refund 500 of invoice", "Different amount");
        long underscore = ticket("SOFTWARE", "Error in user_id field", "Validation");
        ticket("SOFTWARE", "Error in userXid field", "Validation");

        assertThat(ids(list(get("/api/tickets").param("q", "50%")))).containsExactlyElementsOf(ids(percent));
        assertThat(ids(list(get("/api/tickets").param("q", "user_id")))).containsExactlyElementsOf(ids(underscore));
        assertThat(ids(list(get("/api/tickets").param("q", "%")))).containsExactlyElementsOf(ids(percent));
    }

    @Test
    @DisplayName("filters only ever narrow the caller's scope")
    void filtersCannotWidenScope() throws Exception {
        long otherOrg = createOrganization("Other Search Org");
        long outsider = createUser("Other Customer", "CUSTOMER", otherOrg);
        ticket("OTHER", "Mine", "Mine");

        // A customer filtering by another customer's id sees nothing, not that customer's tickets.
        String body = mockMvc.perform(get("/api/tickets").param("customerId", String.valueOf(customer)).with(as(outsider)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(body)).isEmpty();

        // Naming an organization other than your own is reported as not found.
        mockMvc.perform(get("/api/tickets").param("organizationId", String.valueOf(otherOrg)).with(as(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("users can be searched by name or email and filtered by role and active flag")
    void userSearch() throws Exception {
        String body = list(get("/api/users").param("q", "search agent"));
        assertThat(ids(body)).containsExactlyElementsOf(ids(agent));

        body = list(get("/api/users").param("role", "CUSTOMER").param("active", "true"));
        assertThat(ids(body)).containsExactlyElementsOf(ids(customer));

        body = list(get("/api/users").param("sort", "name,desc").param("size", "2"));
        assertThat(JsonPath.<Integer>read(body, "$.totalElements")).isEqualTo(3);
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].name"))
                .containsExactly("Search Customer", "Search Agent");
    }

    @Test
    @DisplayName("organizations can be searched by name and paged")
    void organizationSearch() throws Exception {
        String body = mockMvc.perform(get("/api/organizations").param("q", "search org").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").isArray())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<Integer>>read(body, "$.content[*].id")).contains((int) org);
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].name")).allMatch(name -> name.contains("Search Org"));
    }
}
