package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.config.TicketWorkflowProperties;
import com.ibrahim.helpdesk.ticket.dto.AgentActionRequest;
import com.ibrahim.helpdesk.ticket.dto.AssignTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.CloseTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.ReopenTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.priority.RuleBasedTicketPriorityPolicy;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
    private static final long CUSTOMER_ID = 1L;

    /** The instant the fixed clock reports as "now" for every test. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);
    private static final Duration ADMIN_CLOSE_AFTER = Duration.ofHours(3);
    private static final Duration REOPEN_WINDOW = Duration.ofDays(7);

    @Mock
    private TicketService ticketService;

    @Mock
    private UserService userService;

    @Mock
    private TicketRepository ticketRepository;

    private TicketWorkflowService workflowService;

    private Organization acme;
    private Organization globex;
    private Ticket ticket;
    private User customer;
    private User admin;
    private User agent;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.systemDefault();
        Clock fixedClock = Clock.fixed(NOW.atZone(zone).toInstant(), zone);
        workflowService = new TicketWorkflowService(
                ticketService, userService, ticketRepository, fixedClock,
                new TicketWorkflowProperties(ADMIN_CLOSE_AFTER, REOPEN_WINDOW),
                // The real policy: it is a pure function, so there is nothing to isolate.
                new RuleBasedTicketPriorityPolicy());

        acme = organization(7L, "Acme Ltd");
        globex = organization(8L, "Globex Corp");

        customer = user(CUSTOMER_ID, "Dana Customer", UserRole.CUSTOMER, acme);
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
        ticket.setCreatedAt(NOW.minusHours(2));
        ticket.setUpdatedAt(NOW.minusHours(2));
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
        @EnumSource(value = TicketStatus.class, names = {"RESOLVED", "CLOSED"})
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

    /** Puts the fixture ticket in the hands of the fixture agent with the given status. */
    private void givenAssignedTicket(TicketStatus status) {
        ticket.setStatus(status);
        ticket.setAssignedAgent(agent);
        when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
    }

    private AgentActionRequest asAgent(long agentId) {
        return new AgentActionRequest(agentId);
    }

    @Nested
    @DisplayName("Start work (ASSIGNED -> IN_PROGRESS)")
    class StartWork {

        @Test
        @DisplayName("the assigned agent moves an ASSIGNED ticket to IN_PROGRESS")
        void startsAssignedTicket() {
            givenAssignedTicket(TicketStatus.ASSIGNED);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);
            when(ticketRepository.save(ticket)).thenReturn(ticket);
            LocalDateTime before = ticket.getUpdatedAt();

            TicketResponse response = workflowService.startWork(TICKET_ID, asAgent(AGENT_ID));

            assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
            assertThat(response.updatedAt()).isAfter(before);
            assertThat(response.resolvedAt()).isNull();
            assertThat(response.closedAt()).isNull();
            verify(ticketRepository).save(ticket);
        }

        @Test
        @DisplayName("rejects an unknown ticket before loading the agent")
        void rejectsUnknownTicket() {
            when(ticketService.findOrThrow(TICKET_ID)).thenThrow(new TicketNotFoundException(TICKET_ID));

            assertThatThrownBy(() -> workflowService.startWork(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(TicketNotFoundException.class);

            verify(userService, never()).findOrThrow(any());
            assertNothingSaved();
        }

        @Test
        @DisplayName("rejects an unknown acting user")
        void rejectsUnknownAgent() {
            givenAssignedTicket(TicketStatus.ASSIGNED);
            when(userService.findOrThrow(AGENT_ID)).thenThrow(new UserNotFoundException(AGENT_ID));

            assertThatThrownBy(() -> workflowService.startWork(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(UserNotFoundException.class);

            assertNothingSaved();
        }

        @Test
        @DisplayName("another agent in the same organization cannot start the ticket")
        void rejectsOtherAgent() {
            User otherAgent = user(21L, "Pat Agent", UserRole.SUPPORT_AGENT, acme);
            givenAssignedTicket(TicketStatus.ASSIGNED);
            when(userService.findOrThrow(21L)).thenReturn(otherAgent);

            assertThatThrownBy(() -> workflowService.startWork(TICKET_ID, asAgent(21L)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only the assigned agent can start work on this ticket");

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
            assertNothingSaved();
        }

        @Test
        @DisplayName("nobody can start an unassigned ticket; it is refused as 403, not 409")
        void rejectsUnassignedTicket() {
            when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);

            assertThatThrownBy(() -> workflowService.startWork(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only the assigned agent can start work on this ticket");

            assertNothingSaved();
        }

        @Test
        @DisplayName("an assigned agent who has since been deactivated cannot start")
        void rejectsInactiveAssignedAgent() {
            agent.setActive(false);
            givenAssignedTicket(TicketStatus.ASSIGNED);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);

            assertThatThrownBy(() -> workflowService.startWork(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot start work on tickets");

            assertNothingSaved();
        }

        @Test
        @DisplayName("an assigned user who is no longer a SUPPORT_AGENT cannot start")
        void rejectsAssignedUserWhoseRoleChanged() {
            agent.setRole(UserRole.ORG_ADMIN);
            givenAssignedTicket(TicketStatus.ASSIGNED);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);

            assertThatThrownBy(() -> workflowService.startWork(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(ForbiddenOperationException.class);

            assertNothingSaved();
        }

        @ParameterizedTest(name = "cannot start work on a {0} ticket")
        @EnumSource(value = TicketStatus.class, names = {"IN_PROGRESS", "RESOLVED", "CLOSED"})
        void rejectsNonStartableStatuses(TicketStatus status) {
            givenAssignedTicket(status);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);

            assertThatThrownBy(() -> workflowService.startWork(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("Cannot start work on a ticket with status " + status);

            assertThat(ticket.getStatus()).isEqualTo(status);
            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Resolve (IN_PROGRESS -> RESOLVED)")
    class Resolve {

        @Test
        @DisplayName("the assigned agent resolves an IN_PROGRESS ticket and resolvedAt is set")
        void resolvesInProgressTicket() {
            givenAssignedTicket(TicketStatus.IN_PROGRESS);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.resolveTicket(TICKET_ID, asAgent(AGENT_ID));

            assertThat(response.status()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(response.resolvedAt()).isEqualTo(NOW);
            assertThat(response.updatedAt()).isEqualTo(response.resolvedAt());
            assertThat(response.closedAt()).isNull();
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
            assertThat(response.reopenCount()).isZero();
        }

        @ParameterizedTest(name = "a {0} who is not the assigned agent cannot resolve")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "ORG_ADMIN", "SUPPORT_AGENT", "SUPER_ADMIN"})
        void rejectsAnyoneButTheAssignedAgent(UserRole role) {
            User actor = user(30L, "Someone Else", role, acme);
            givenAssignedTicket(TicketStatus.IN_PROGRESS);
            when(userService.findOrThrow(30L)).thenReturn(actor);

            assertThatThrownBy(() -> workflowService.resolveTicket(TICKET_ID, asAgent(30L)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only the assigned agent can resolve this ticket");

            assertThat(ticket.getResolvedAt()).isNull();
            assertNothingSaved();
        }

        @Test
        @DisplayName("an assigned agent who has since been deactivated cannot resolve")
        void rejectsInactiveAssignedAgent() {
            agent.setActive(false);
            givenAssignedTicket(TicketStatus.IN_PROGRESS);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);

            assertThatThrownBy(() -> workflowService.resolveTicket(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot resolve tickets");

            assertNothingSaved();
        }

        @ParameterizedTest(name = "cannot resolve a {0} ticket")
        @EnumSource(value = TicketStatus.class, names = {"ASSIGNED", "RESOLVED", "REOPENED", "CLOSED"})
        void rejectsNonResolvableStatuses(TicketStatus status) {
            givenAssignedTicket(status);
            LocalDateTime originalResolvedAt = ticket.getResolvedAt();
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);

            assertThatThrownBy(() -> workflowService.resolveTicket(TICKET_ID, asAgent(AGENT_ID)))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("Cannot resolve a ticket with status " + status);

            assertThat(ticket.getStatus()).isEqualTo(status);
            assertThat(ticket.getResolvedAt()).isEqualTo(originalResolvedAt);
            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Reopened tickets in assign and start")
    class ReopenedTickets {

        @Test
        @DisplayName("an admin can hand a REOPENED ticket to a different agent, which makes it ASSIGNED")
        void adminReassignsReopenedTicket() {
            User previousAgent = user(21L, "Pat Agent", UserRole.SUPPORT_AGENT, acme);
            ticket.setStatus(TicketStatus.REOPENED);
            ticket.setAssignedAgent(previousAgent);
            givenTicketAdminAndAgent();
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.status()).isEqualTo(TicketStatus.ASSIGNED);
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("assigning a REOPENED ticket to the agent it already has changes nothing")
        void assigningReopenedTicketToSameAgentIsNoOp() {
            ticket.setStatus(TicketStatus.REOPENED);
            ticket.setAssignedAgent(agent);
            givenTicketAdminAndAgent();

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.status()).isEqualTo(TicketStatus.REOPENED);
            assertNothingSaved();
        }

        @Test
        @DisplayName("the agent who kept a REOPENED ticket can start work on it again")
        void assignedAgentRestartsReopenedTicket() {
            givenAssignedTicket(TicketStatus.REOPENED);
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.startWork(TICKET_ID, asAgent(AGENT_ID));

            assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(response.updatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("Reassigning in-progress tickets")
    class InProgressReassignment {

        @Test
        @DisplayName("an admin moves an IN_PROGRESS ticket to a different agent, sending it back to ASSIGNED")
        void reassignsInProgressTicket() {
            User previousAgent = user(21L, "Pat Agent", UserRole.SUPPORT_AGENT, acme);
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setAssignedAgent(previousAgent);
            givenTicketAdminAndAgent();
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.status()).isEqualTo(TicketStatus.ASSIGNED);
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
            assertThat(response.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("assigning an IN_PROGRESS ticket to its current agent does not undo their progress")
        void sameAgentKeepsInProgress() {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setAssignedAgent(agent);
            givenTicketAdminAndAgent();

            TicketResponse response = workflowService.assignTicket(TICKET_ID, request());

            assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertNothingSaved();
        }
    }

    /** A ticket the fixture agent resolved at the given time. */
    private void givenResolvedTicket(LocalDateTime resolvedAt) {
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setAssignedAgent(agent);
        ticket.setResolvedAt(resolvedAt);
        when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
    }

    /** A ticket that was resolved and then closed at the given time. */
    private void givenClosedTicket(LocalDateTime closedAt) {
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setAssignedAgent(agent);
        ticket.setResolvedAt(closedAt.minusHours(1));
        ticket.setClosedAt(closedAt);
        when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
    }

    @Nested
    @DisplayName("Reopen (RESOLVED or CLOSED -> REOPENED)")
    class Reopen {

        private final ReopenTicketRequest byCustomer = new ReopenTicketRequest(CUSTOMER_ID);

        @Test
        @DisplayName("the customer reopens a RESOLVED ticket; the agent is kept and timestamps are cleared")
        void reopensResolvedTicket() {
            givenResolvedTicket(NOW.minusMinutes(30));
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.reopenTicket(TICKET_ID, byCustomer);

            assertThat(response.status()).isEqualTo(TicketStatus.REOPENED);
            assertThat(response.reopenCount()).isEqualTo(1);
            // A MEDIUM hardware ticket escalates one level on its first reopen.
            assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
            assertThat(response.resolvedAt()).isNull();
            assertThat(response.closedAt()).isNull();
            assertThat(response.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("each reopen increments reopenCount")
        void incrementsExistingReopenCount() {
            givenResolvedTicket(NOW.minusMinutes(30));
            ticket.setReopenCount(2);
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            assertThat(workflowService.reopenTicket(TICKET_ID, byCustomer).reopenCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("reopening recalculates priority from the ticket's content and new reopen count")
        void reopenRecalculatesPriority() {
            givenResolvedTicket(NOW.minusMinutes(30));
            ticket.setCategory(TicketCategory.OTHER);
            ticket.setTitle("Question about fonts");
            ticket.setDescription("How do I change the default font?");
            ticket.setPriority(TicketPriority.LOW);
            ticket.setReopenCount(1);
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.reopenTicket(TICKET_ID, byCustomer);

            assertThat(response.reopenCount()).isEqualTo(2);
            assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
        }

        @Test
        @DisplayName("a CLOSED ticket can be reopened right up to the end of the reopen window")
        void reopensClosedTicketAtWindowBoundary() {
            givenClosedTicket(NOW.minus(REOPEN_WINDOW));
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.reopenTicket(TICKET_ID, byCustomer);

            assertThat(response.status()).isEqualTo(TicketStatus.REOPENED);
            assertThat(response.closedAt()).isNull();
        }

        @Test
        @DisplayName("a CLOSED ticket past the reopen window is refused with 409")
        void rejectsClosedTicketPastWindow() {
            givenClosedTicket(NOW.minus(REOPEN_WINDOW).minusMinutes(1));
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);

            assertThatThrownBy(() -> workflowService.reopenTicket(TICKET_ID, byCustomer))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("Tickets can only be reopened within 7 days of being closed; please open a new ticket");

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
            assertNothingSaved();
        }

        @ParameterizedTest(name = "a {0} who is not the ticket's customer cannot reopen")
        @EnumSource(UserRole.class)
        void rejectsAnyoneButTheTicketCustomer(UserRole role) {
            User someoneElse = user(30L, "Someone Else", role, acme);
            givenResolvedTicket(NOW.minusMinutes(30));
            when(userService.findOrThrow(30L)).thenReturn(someoneElse);

            assertThatThrownBy(() -> workflowService.reopenTicket(TICKET_ID, new ReopenTicketRequest(30L)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only the customer who opened this ticket can reopen it");

            assertNothingSaved();
        }

        @Test
        @DisplayName("an inactive customer cannot reopen")
        void rejectsInactiveCustomer() {
            customer.setActive(false);
            givenResolvedTicket(NOW.minusMinutes(30));
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);

            assertThatThrownBy(() -> workflowService.reopenTicket(TICKET_ID, byCustomer))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot reopen tickets");

            assertNothingSaved();
        }

        @ParameterizedTest(name = "cannot reopen a {0} ticket")
        @EnumSource(value = TicketStatus.class, names = {"OPEN", "ASSIGNED", "IN_PROGRESS", "REOPENED"})
        void rejectsNonReopenableStatuses(TicketStatus status) {
            ticket.setStatus(status);
            when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);

            assertThatThrownBy(() -> workflowService.reopenTicket(TICKET_ID, byCustomer))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("Cannot reopen a ticket with status " + status);

            assertThat(ticket.getReopenCount()).isZero();
            assertNothingSaved();
        }

        @Test
        @DisplayName("an unknown customer is a 404")
        void rejectsUnknownCustomer() {
            givenResolvedTicket(NOW.minusMinutes(30));
            when(userService.findOrThrow(CUSTOMER_ID)).thenThrow(new UserNotFoundException(CUSTOMER_ID));

            assertThatThrownBy(() -> workflowService.reopenTicket(TICKET_ID, byCustomer))
                    .isInstanceOf(UserNotFoundException.class);

            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Close (RESOLVED -> CLOSED)")
    class Close {

        @Test
        @DisplayName("the customer closes a ticket the moment it is resolved; no grace period applies to them")
        void customerClosesImmediately() {
            givenResolvedTicket(NOW);
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(CUSTOMER_ID));

            assertThat(response.status()).isEqualTo(TicketStatus.CLOSED);
            assertThat(response.closedAt()).isEqualTo(NOW);
            assertThat(response.updatedAt()).isEqualTo(NOW);
            assertThat(response.resolvedAt()).isEqualTo(NOW);
            assertThat(response.assignedAgent().id()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("an admin cannot close before the customer's window has passed, and is told when they can")
        void adminCannotCloseDuringCustomerWindow() {
            givenResolvedTicket(NOW.minus(ADMIN_CLOSE_AFTER).plusMinutes(1));
            when(userService.findOrThrow(ADMIN_ID)).thenReturn(admin);

            assertThatThrownBy(() -> workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(ADMIN_ID)))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("The customer has 3 hours after resolution to close this ticket; "
                            + "an administrator can close it from 2026-09-14T12:01");

            assertThat(ticket.getClosedAt()).isNull();
            assertNothingSaved();
        }

        @Test
        @DisplayName("an admin can close exactly when the customer's window ends")
        void adminClosesAtWindowBoundary() {
            givenResolvedTicket(NOW.minus(ADMIN_CLOSE_AFTER));
            when(userService.findOrThrow(ADMIN_ID)).thenReturn(admin);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response = workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(ADMIN_ID));

            assertThat(response.status()).isEqualTo(TicketStatus.CLOSED);
            assertThat(response.closedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("an admin can close long after the customer's window ends")
        void adminClosesAfterWindow() {
            givenResolvedTicket(NOW.minusDays(2));
            when(userService.findOrThrow(ADMIN_ID)).thenReturn(admin);
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            assertThat(workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(ADMIN_ID)).status())
                    .isEqualTo(TicketStatus.CLOSED);
        }

        @Test
        @DisplayName("an admin from another organization cannot close, even after the window")
        void rejectsAdminFromOtherOrganization() {
            admin.setOrganization(globex);
            givenResolvedTicket(NOW.minusDays(2));
            when(userService.findOrThrow(ADMIN_ID)).thenReturn(admin);

            assertThatThrownBy(() -> workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(ADMIN_ID)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only the ticket's customer or an administrator of its organization can close this ticket");

            assertNothingSaved();
        }

        @Test
        @DisplayName("the assigned agent cannot close the ticket they resolved")
        void rejectsAssignedAgent() {
            givenResolvedTicket(NOW.minusDays(2));
            when(userService.findOrThrow(AGENT_ID)).thenReturn(agent);

            assertThatThrownBy(() -> workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(AGENT_ID)))
                    .isInstanceOf(ForbiddenOperationException.class);

            assertNothingSaved();
        }

        @ParameterizedTest(name = "a different {0} cannot close")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT", "SUPER_ADMIN"})
        void rejectsOtherUsers(UserRole role) {
            User someoneElse = user(30L, "Someone Else", role, acme);
            givenResolvedTicket(NOW.minusDays(2));
            when(userService.findOrThrow(30L)).thenReturn(someoneElse);

            assertThatThrownBy(() -> workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(30L)))
                    .isInstanceOf(ForbiddenOperationException.class);

            assertNothingSaved();
        }

        @Test
        @DisplayName("an inactive customer cannot close")
        void rejectsInactiveCustomer() {
            customer.setActive(false);
            givenResolvedTicket(NOW);
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);

            assertThatThrownBy(() -> workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(CUSTOMER_ID)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot close tickets");

            assertNothingSaved();
        }

        @Test
        @DisplayName("an inactive admin cannot close")
        void rejectsInactiveAdmin() {
            admin.setActive(false);
            givenResolvedTicket(NOW.minusDays(2));
            when(userService.findOrThrow(ADMIN_ID)).thenReturn(admin);

            assertThatThrownBy(() -> workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(ADMIN_ID)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot close tickets");

            assertNothingSaved();
        }

        @ParameterizedTest(name = "cannot close a {0} ticket")
        @EnumSource(value = TicketStatus.class, names = {"OPEN", "ASSIGNED", "IN_PROGRESS", "REOPENED", "CLOSED"})
        void rejectsNonClosableStatuses(TicketStatus status) {
            ticket.setStatus(status);
            when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
            when(userService.findOrThrow(CUSTOMER_ID)).thenReturn(customer);

            assertThatThrownBy(() -> workflowService.closeTicket(TICKET_ID, new CloseTicketRequest(CUSTOMER_ID)))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("Cannot close a ticket with status " + status);

            assertNothingSaved();
        }
    }

    @Nested
    @DisplayName("Window descriptions in error messages")
    class Describe {

        @Test
        void describesWholeDaysHoursAndMinutes() {
            assertThat(TicketWorkflowService.describe(Duration.ofDays(7))).isEqualTo("7 days");
            assertThat(TicketWorkflowService.describe(Duration.ofDays(1))).isEqualTo("1 day");
            assertThat(TicketWorkflowService.describe(Duration.ofHours(3))).isEqualTo("3 hours");
            assertThat(TicketWorkflowService.describe(Duration.ofMinutes(90))).isEqualTo("90 minutes");
        }
    }
}
