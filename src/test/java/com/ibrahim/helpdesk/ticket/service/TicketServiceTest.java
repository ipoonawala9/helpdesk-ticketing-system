package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.dto.CreateTicketRequest;
import com.ibrahim.helpdesk.ticket.dto.TicketResponse;
import com.ibrahim.helpdesk.ticket.dto.UpdateTicketRequest;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketCategory;
import com.ibrahim.helpdesk.ticket.entity.TicketPriority;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.priority.PriorityInput;
import com.ibrahim.helpdesk.ticket.priority.TicketPriorityPolicy;
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserService userService;

    @Mock
    private TicketPriorityPolicy priorityPolicy;

    @InjectMocks
    private TicketService ticketService;

    private Organization organization;
    private User customer;

    @BeforeEach
    void setUp() {
        organization = new Organization();
        organization.setId(7L);
        organization.setName("Acme Ltd");
        organization.setCompanyEmail("support@acme.test");
        organization.setDomain("acme.test");
        organization.setIndustry("Manufacturing");

        customer = new User();
        customer.setId(1L);
        customer.setName("Dana Customer");
        customer.setEmail("dana@acme.test");
        customer.setPassword("super-secret");
        customer.setRole(UserRole.CUSTOMER);
        customer.setActive(true);
        customer.setOrganization(organization);
    }

    /** Mimics the database assigning an id on first save. */
    private void stubSaveAssigningId(long id) {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            if (t.getId() == null) {
                t.setId(id);
            }
            return t;
        });
    }

    @Test
    @DisplayName("createTicket derives organization from the customer and sets all server-controlled fields")
    void createTicketSetsServerControlledFields() {
        when(userService.findOrThrow(1L)).thenReturn(customer);
        when(priorityPolicy.determine(any(PriorityInput.class))).thenReturn(TicketPriority.HIGH);
        stubSaveAssigningId(42L);

        TicketResponse response = ticketService.createTicket(new CreateTicketRequest(
                "Printer will not print", "It jams on every job", TicketCategory.HARDWARE), 1L);

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
        verify(priorityPolicy).determine(new PriorityInput(
                TicketCategory.HARDWARE, "Printer will not print", "It jams on every job", 0));
        assertThat(response.assignedAgent()).isNull();
        assertThat(response.reopenCount()).isZero();
        assertThat(response.resolvedAt()).isNull();
        assertThat(response.closedAt()).isNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.organization().id()).isEqualTo(7L);
        assertThat(response.customer().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("createTicket generates the public ticket number from the database id")
    void createTicketGeneratesTicketNumber() {
        when(userService.findOrThrow(1L)).thenReturn(customer);
        stubSaveAssigningId(42L);

        TicketResponse response = ticketService.createTicket(new CreateTicketRequest(
                "Cannot log in", "Password reset never arrives", TicketCategory.ACCOUNT), 1L);

        assertThat(response.ticketNumber()).isEqualTo("HD-2026-000042");
    }

    @Test
    @DisplayName("createTicket fails when the customer does not exist and never touches the ticket table")
    void createTicketRejectsUnknownCustomer() {
        when(userService.findOrThrow(99L)).thenThrow(new UserNotFoundException(99L));

        assertThatThrownBy(() -> ticketService.createTicket(new CreateTicketRequest(
                "Broken laptop", "Screen is cracked", TicketCategory.HARDWARE), 99L))
                .isInstanceOf(UserNotFoundException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("updateTicket changes title, description and category, recalculates priority, and leaves workflow fields alone")
    void updateTicketLeavesWorkflowFieldsAlone() {
        Ticket existing = new Ticket();
        existing.setId(5L);
        existing.setTicketNumber("HD-2026-000005");
        existing.setTitle("Old title");
        existing.setDescription("Old description");
        existing.setCategory(TicketCategory.OTHER);
        existing.setStatus(TicketStatus.IN_PROGRESS);
        existing.setPriority(TicketPriority.HIGH);
        existing.setCustomer(customer);
        existing.setOrganization(organization);
        existing.setReopenCount(2);
        existing.setCreatedAt(LocalDateTime.now().minusDays(3));

        when(ticketRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userService.findOrThrow(1L)).thenReturn(customer);
        when(priorityPolicy.determine(any(PriorityInput.class))).thenReturn(TicketPriority.MEDIUM);
        stubSaveAssigningId(5L);

        TicketResponse response = ticketService.updateTicket(5L, new UpdateTicketRequest(
                "New title", "New description", TicketCategory.SOFTWARE), 1L);

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.category()).isEqualTo(TicketCategory.SOFTWARE);

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.reopenCount()).isEqualTo(2);
        assertThat(response.ticketNumber()).isEqualTo("HD-2026-000005");

        // Priority is recalculated from the edited content and the existing reopen count.
        assertThat(response.priority()).isEqualTo(TicketPriority.MEDIUM);
        verify(priorityPolicy).determine(new PriorityInput(
                TicketCategory.SOFTWARE, "New title", "New description", 2));
    }

    @Test
    @DisplayName("getTicketById reports a missing ticket as TicketNotFoundException")
    void getTicketByIdRejectsMissingTicket() {
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getTicketById(404L, 1L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("404");
    }

    private User user(long id, UserRole role, Organization org) {
        User user = new User();
        user.setId(id);
        user.setName(role + " " + id);
        user.setRole(role);
        user.setActive(true);
        user.setOrganization(org);
        return user;
    }

    private Ticket existingTicket(User assignedAgent) {
        Ticket ticket = new Ticket();
        ticket.setId(5L);
        ticket.setTitle("Printer");
        ticket.setDescription("Jams");
        ticket.setCategory(TicketCategory.HARDWARE);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setCustomer(customer);
        ticket.setAssignedAgent(assignedAgent);
        ticket.setOrganization(organization);
        ticket.setReopenCount(0);
        return ticket;
    }

    @Test
    @DisplayName("createTicket refuses a user who is not a CUSTOMER")
    void createTicketRequiresCustomerRole() {
        User agent = user(20L, UserRole.SUPPORT_AGENT, organization);
        when(userService.findOrThrow(20L)).thenReturn(agent);

        assertThatThrownBy(() -> ticketService.createTicket(
                new CreateTicketRequest("Title", "Description", TicketCategory.OTHER), 20L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only customers can open tickets");

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("getTicketById lets the customer, the assigned agent, an org admin and a super admin read the ticket")
    void getTicketByIdAllowedViewers() {
        User agent = user(20L, UserRole.SUPPORT_AGENT, organization);
        Ticket ticket = existingTicket(agent);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        User orgAdmin = user(10L, UserRole.ORG_ADMIN, organization);
        User superAdmin = user(99L, UserRole.SUPER_ADMIN, null);

        for (User viewer : java.util.List.of(customer, agent, orgAdmin, superAdmin)) {
            when(userService.findOrThrow(viewer.getId())).thenReturn(viewer);
            assertThat(ticketService.getTicketById(5L, viewer.getId()).id()).isEqualTo(5L);
        }
    }

    @Test
    @DisplayName("getTicketById refuses other customers, unassigned agents and admins of other organizations")
    void getTicketByIdRefusedViewers() {
        Organization other = new Organization();
        other.setId(8L);
        Ticket ticket = existingTicket(user(20L, UserRole.SUPPORT_AGENT, organization));
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        for (User viewer : java.util.List.of(
                user(2L, UserRole.CUSTOMER, organization),
                user(21L, UserRole.SUPPORT_AGENT, organization),
                user(11L, UserRole.ORG_ADMIN, other))) {
            when(userService.findOrThrow(viewer.getId())).thenReturn(viewer);
            assertThatThrownBy(() -> ticketService.getTicketById(5L, viewer.getId()))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("You do not have access to this ticket");
        }
    }

    @Test
    @DisplayName("updateTicket refuses anyone but the ticket's customer and changes nothing")
    void updateTicketOnlyByOwner() {
        Ticket ticket = existingTicket(null);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        User otherCustomer = user(2L, UserRole.CUSTOMER, organization);
        when(userService.findOrThrow(2L)).thenReturn(otherCustomer);

        assertThatThrownBy(() -> ticketService.updateTicket(
                5L, new UpdateTicketRequest("Hijacked", "Hijacked", TicketCategory.OTHER), 2L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only the customer who opened this ticket can edit it");

        assertThat(ticket.getTitle()).isEqualTo("Printer");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("deleteTicket is allowed for an admin of the ticket's organization")
    void deleteTicketByOrgAdmin() {
        Ticket ticket = existingTicket(null);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(userService.findOrThrow(10L)).thenReturn(user(10L, UserRole.ORG_ADMIN, organization));

        ticketService.deleteTicket(5L, 10L);

        verify(ticketRepository).delete(ticket);
    }

    @Test
    @DisplayName("deleteTicket refuses the customer and admins of other organizations")
    void deleteTicketRefused() {
        Organization other = new Organization();
        other.setId(8L);
        Ticket ticket = existingTicket(null);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        for (User deleter : java.util.List.of(customer, user(11L, UserRole.ORG_ADMIN, other))) {
            when(userService.findOrThrow(deleter.getId())).thenReturn(deleter);
            assertThatThrownBy(() -> ticketService.deleteTicket(5L, deleter.getId()))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
        verify(ticketRepository, never()).delete(any(Ticket.class));
    }
}
