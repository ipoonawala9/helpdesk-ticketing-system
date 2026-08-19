package com.ibrahim.helpdesk.ticket.service;

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
import com.ibrahim.helpdesk.ticket.repository.TicketRepository;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
        stubSaveAssigningId(42L);

        TicketResponse response = ticketService.createTicket(new CreateTicketRequest(
                "Printer will not print", "It jams on every job", TicketCategory.HARDWARE, 1L));

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
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
                "Cannot log in", "Password reset never arrives", TicketCategory.ACCOUNT, 1L));

        assertThat(response.ticketNumber()).isEqualTo("HD-2026-000042");
    }

    @Test
    @DisplayName("createTicket fails when the customer does not exist and never touches the ticket table")
    void createTicketRejectsUnknownCustomer() {
        when(userService.findOrThrow(99L)).thenThrow(new UserNotFoundException(99L));

        assertThatThrownBy(() -> ticketService.createTicket(new CreateTicketRequest(
                "Broken laptop", "Screen is cracked", TicketCategory.HARDWARE, 99L)))
                .isInstanceOf(UserNotFoundException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("updateTicket changes only title, description and category")
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
        stubSaveAssigningId(5L);

        TicketResponse response = ticketService.updateTicket(5L, new UpdateTicketRequest(
                "New title", "New description", TicketCategory.SOFTWARE));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.category()).isEqualTo(TicketCategory.SOFTWARE);

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(response.reopenCount()).isEqualTo(2);
        assertThat(response.ticketNumber()).isEqualTo("HD-2026-000005");
    }

    @Test
    @DisplayName("getTicketById reports a missing ticket as TicketNotFoundException")
    void getTicketByIdRejectsMissingTicket() {
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getTicketById(404L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("404");
    }
}
