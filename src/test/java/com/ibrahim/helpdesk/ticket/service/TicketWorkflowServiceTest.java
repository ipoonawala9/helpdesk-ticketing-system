package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketWorkflowServiceTest {

    private static final long TICKET_ID = 42L;
    private static final long ADMIN_ID = 10L;
    private static final long AGENT_ID = 20L;

    @Mock
    private TicketService ticketService;

    @Mock
    private UserService userService;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketWorkflowService workflowService;

    private Organization acme;
    private Organization globex;
    private Ticket ticket;
    private User admin;
    private User agent;

    @BeforeEach
    void setUp() {
        acme = organization(7L, "Acme Ltd");
        globex = organization(8L, "Globex Corp");

        User customer = user(1L, "Dana Customer", UserRole.CUSTOMER, acme);
        admin = user(ADMIN_ID, "Alex Admin", UserRole.ORG_ADMIN, acme);
        agent = user(AGENT_ID, "Sam Agent", UserRole.SUPPORT_AGENT, acme);

        ticket = new Ticket();
        ticket.setId(TICKET_ID);
        ticket.setTicketNumber("HD-2026-000042");
        ticket.setTitle("Printer will not print");
        ticket.setDescription("It jams on every job");
        ticket.setCategory(TicketCategory.HARDWARE);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCustomer(customer);
        ticket.setOrganization(acme);
        ticket.setReopenCount(0);
        ticket.setCreatedAt(LocalDateTime.now().minusHours(2));
        ticket.setUpdatedAt(LocalDateTime.now().minusHours(2));
    }

    private static Organization organization(long id, String name) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        return organization;
    }

    private static User user(long id, String name, UserRole role, Organization organization) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "@example.test");
        user.setRole(role);
        user.setActive(true);
        user.setOrganization(organization);
        return user;
    }

    private AssignTicketRequest request() {
        return new AssignTicketRequest(AGENT_ID, ADMIN_ID);
    }

    private void givenTicketAndAdmin() {
        when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
        when(userService.findOrThrow(ADMIN_ID)).thenReturn(admin);
    }

    private void givenTicketAdminAndAgent() {
        givenTicketAndAdmin();
        when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);
    }

    private void assertNothingSaved() {
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Nested
    @DisplayName("Valid assignments")
    class ValidAssignments {

        @Test
        @DisplayName("assigns an OPEN ticket, sets status ASSIGNED and bumps updatedAt")
        void assignsOpenTicket() {
            givenTicketAdminAndAgent();
            when(ticketRepository.save(ticket)).thenReturn(ticket);
            LocalDateTime before = ticket.getUpdatedAt();

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.status()).isEqualTo(TicketStatus.ASSIGNED);
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
            assertThat(response.assignedAgent().role()).isEqualTo(UserRole.SUPPORT_AGENT);
            assertThat(response.updatedAt()).isAfter(before);
            verify(ticketRepository).save(ticket);
        }

        @Test
        @DisplayName("leaves every non-assignment field untouched")
        void doesNotTouchOtherFields() {
            givenTicketAdminAndAgent();
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.ticketNumber()).isEqualTo("HD-2026-000042");
            assertThat(response.customer().id()).isEqualTo(1L);
            assertThat(response.organization().id()).isEqualTo(7L);
            assertThat(response.reopenCount()).isZero();
            assertThat(response.resolvedAt()).isNull();
            assertThat(response.closedAt()).isNull();
        }

        @Test
        @DisplayName("reassigns an ASSIGNED ticket to a different agent in the same organization")
        void reassignsAssignedTicket() {
            User previousAgent = user(21L, "Pat Agent", UserRole.SUPPORT_AGENT, acme);
            ticket.setStatus(TicketStatus.ASSIGNED);
            ticket.setAssignedAgent(previousAgent);
            givenTicketAdminAndAgent();
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.status()).isEqualTo(TicketStatus.ASSIGNED);
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("re-assigning the current agent is an idempotent no-op")
        void reassigningSameAgentIsNoOp() {
            ticket.setStatus(TicketStatus.ASSIGNED);
            ticket.setAssignedAgent(agent);
            LocalDateTime before = ticket.getUpdatedAt();
            givenTicketAdminAndAgent();

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
            assertThat(response.updatedAt()).isEqualTo(before);
            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Missing resources")
    class MissingResources {

        @Test
        @DisplayName("rejects an unknown ticket before loading any user")
        void rejectsUnknownTicket() {
            when(ticketService.findOrThrow(TICKET_ID)).thenThrow(new TicketNotFoundException(TICKET_ID));

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(TicketNotFoundException.class);

            verify(userService, never()).findOrThrow(any());
            assertNothingSaved();
        }

        @Test
        @DisplayName("rejects an unknown admin")
        void rejectsUnknownAdmin() {
            when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
            when(userService.findOrThrow(ADMIN_ID)).thenThrow(new UserNotFoundException(ADMIN_ID));

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(UserNotFoundException.class);

            assertNothingSaved();
        }

        @Test
        @DisplayName("rejects an unknown agent")
        void rejectsUnknownAgent() {
            givenTicketAndAdmin();
            when(userService.findOrThrow(AGENT_ID)).thenThrow(new UserNotFoundException(AGENT_ID));

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(String.valueOf(AGENT_ID));

            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Acting admin is not permitted")
    class ForbiddenActor {

        @ParameterizedTest(name = "{0} cannot assign")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT", "SUPER_ADMIN"})
        @DisplayName("only ORG_ADMIN may assign")
        void rejectsNonOrgAdminRoles(UserRole role) {
            admin.setRole(role);
            givenTicketAndAdmin();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only organization administrators can assign tickets");

            verify(userService, never()).findOrThrow(AGENT_ID);
            assertNothingSaved();
        }

        @Test
        @DisplayName("an inactive admin cannot assign")
        void rejectsInactiveAdmin() {
            admin.setActive(false);
            givenTicketAndAdmin();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot assign tickets");

            assertNothingSaved();
        }

        @Test
        @DisplayName("an admin from another organization cannot assign, and learns nothing about the agent")
        void rejectsAdminFromOtherOrganization() {
            admin.setOrganization(globex);
            givenTicketAndAdmin();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Administrators can only assign tickets from their own organization");

            verify(userService, never()).findOrThrow(AGENT_ID);
            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Target agent is not assignable")
    class InvalidAgent {

        @ParameterizedTest(name = "cannot assign to {0}")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "ORG_ADMIN", "SUPER_ADMIN"})
        @DisplayName("agent must have role SUPPORT_AGENT")
        void rejectsNonAgentRoles(UserRole role) {
            agent.setRole(role);
            givenTicketAdminAndAgent();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Tickets can only be assigned to users with role SUPPORT_AGENT");

            assertNothingSaved();
        }

        @Test
        @DisplayName("agent must be active")
        void rejectsInactiveAgent() {
            agent.setActive(false);
            givenTicketAdminAndAgent();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Tickets cannot be assigned to an inactive agent");

            assertNothingSaved();
        }

        @Test
        @DisplayName("agent must belong to the ticket's organization")
        void rejectsAgentFromOtherOrganization() {
            agent.setOrganization(globex);
            givenTicketAdminAndAgent();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Agent must belong to the ticket's organization");

            assertThat(ticket.getAssignedAgent()).isNull();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
            assertNothingSaved();
        }

        @Test
        @DisplayName("agent without any organization is rejected")
        void rejectsAgentWithoutOrganization() {
            agent.setOrganization(null);
            givenTicketAdminAndAgent();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Agent must belong to the ticket's organization");

            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Ticket status does not allow assignment")
    class InvalidStatus {

        @ParameterizedTest(name = "cannot assign a {0} ticket")
        @EnumSource(value = TicketStatus.class, names = {"IN_PROGRESS", "RESOLVED", "REOPENED", "CLOSED"})
        void rejectsNonAssignableStatuses(TicketStatus status) {
            ticket.setStatus(status);
            givenTicketAndAdmin();

            assertThatThrownBy(() -> workflowService.assignTicket(TICKET_ID, request()))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("Cannot assign a ticket with status " + status);

            assertThat(ticket.getStatus()).isEqualTo(status);
            assertNothingSaved();
        }
    }
}
