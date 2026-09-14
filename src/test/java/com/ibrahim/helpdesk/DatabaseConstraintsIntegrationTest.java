package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The database itself enforces the data rules, independently of application
 * code. Statements here go straight to JDBC so nothing in the service layer can
 * stand in for a missing constraint. Runs against H2 by default and against
 * PostgreSQL with the {@code postgres} test profile.
 */
class DatabaseConstraintsIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private long org;
    private long admin;
    private long customer;
    private long agent;
    private long ticketId;

    @BeforeEach
    void setUp() throws Exception {
        org = createOrganization("Constraint Org");
        admin = createUser("Constraint Admin", "ORG_ADMIN", org);
        customer = createUser("Constraint Customer", "CUSTOMER", org);
        agent = createUser("Constraint Agent", "SUPPORT_AGENT", org);
        ticketId = createTicket(customer);
        assign(ticketId, agent, admin).andExpect(status().isOk());
    }

    @Test
    @DisplayName("deleting an agent keeps their tickets, now unassigned, and keeps their messages without a sender")
    void deletingAnAgentUnassignsTheirTickets() throws Exception {
        postMessage(ticketId, agent, "On it").andExpect(status().isCreated());

        jdbc.update("DELETE FROM users WHERE id = ?", agent);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE id = ?", Integer.class, ticketId)).isOne();
        assertThat(jdbc.queryForObject("SELECT assigned_agent_id FROM tickets WHERE id = ?", Long.class, ticketId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE ticket_id = ? AND sender_id IS NULL", Integer.class, ticketId)).isOne();

        // The API reads the orphaned rows without failing.
        assertThat(JsonPath.<Object>read(fetchTicket(ticketId), "$.assignedAgent")).isNull();
    }

    @Test
    @DisplayName("a customer who still has tickets cannot be deleted")
    void customerWithTicketsCannotBeDeleted() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", customer))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an organization that still has users or tickets cannot be deleted")
    void organizationInUseCannotBeDeleted() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM organizations WHERE id = ?", org))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("two tickets cannot share a ticket number")
    void ticketNumbersAreUnique() throws Exception {
        long other = createTicket(customer);
        String number = JsonPath.read(fetchTicket(ticketId), "$.ticketNumber");

        assertThatThrownBy(() -> jdbc.update("UPDATE tickets SET ticket_number = ? WHERE id = ?", number, other))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("two users cannot share an email")
    void emailsAreUnique() {
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, customer);

        assertThatThrownBy(() -> jdbc.update("UPDATE users SET email = ? WHERE id = ?", email, agent))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("required ticket columns reject NULL")
    void requiredTicketColumns() {
        for (String column : new String[] {
                "title", "description", "status", "priority", "category",
                "customer_id", "organization_id", "reopen_count", "created_at", "updated_at"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tickets SET " + column + " = NULL WHERE id = ?", ticketId))
                    .as(column)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("required user columns reject NULL; organization stays optional for super admins")
    void requiredUserColumns() {
        for (String column : new String[] {"name", "email", "password", "role", "active"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE users SET " + column + " = NULL WHERE id = ?", customer))
                    .as(column)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbc.queryForObject("SELECT organization_id FROM users WHERE id = ?", Long.class, superAdminId()))
                .isNull();
    }

    @Test
    @DisplayName("an unknown enum value is rejected by the database, not just by the application")
    void enumColumnsAreChecked() {
        for (String column : new String[] {"status", "priority", "category"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE tickets SET " + column + " = 'BANANA' WHERE id = ?", ticketId))
                    .as(column)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role = 'BANANA' WHERE id = ?", customer))
                .as("role")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the indexes behind the scoped list queries exist")
    void indexesExist() throws Exception {
        assertThat(indexNames("tickets")).contains(
                "idx_tickets_organization_created_at",
                "idx_tickets_customer_created_at",
                "idx_tickets_assigned_agent_created_at",
                "idx_tickets_status");
        assertThat(indexNames("users")).contains("idx_users_organization_role");
        assertThat(indexNames("messages")).contains("idx_messages_ticket_created_at");
    }

    private Set<String> indexNames(String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String tableName = metaData.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table;
            try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
                while (indexes.next()) {
                    String name = indexes.getString("INDEX_NAME");
                    if (name != null) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return names;
    }
}
