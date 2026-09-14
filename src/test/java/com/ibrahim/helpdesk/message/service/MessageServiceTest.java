package com.ibrahim.helpdesk.message.service;

import com.ibrahim.helpdesk.exception.ForbiddenOperationException;
import com.ibrahim.helpdesk.exception.InvalidTicketStateException;
import com.ibrahim.helpdesk.exception.TicketNotFoundException;
import com.ibrahim.helpdesk.exception.UserNotFoundException;
import com.ibrahim.helpdesk.message.dto.MessageResponse;
import com.ibrahim.helpdesk.message.dto.PostMessageRequest;
import com.ibrahim.helpdesk.message.entity.Message;
import com.ibrahim.helpdesk.message.repository.MessageRepository;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.ticket.entity.TicketStatus;
import com.ibrahim.helpdesk.ticket.service.TicketService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final long TICKET_ID = 42L;
    private static final long CUSTOMER_ID = 1L;
    private static final long ADMIN_ID = 10L;
    private static final long AGENT_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private UserService userService;

    private MessageService messageService;

    private Organization acme;
    private Organization globex;
    private User customer;
    private User admin;
    private User agent;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.systemDefault();
        messageService = new MessageService(messageRepository, ticketService, userService,
                Clock.fixed(NOW.atZone(zone).toInstant(), zone));

        acme = organization(7L);
        globex = organization(8L);
        customer = user(CUSTOMER_ID, UserRole.CUSTOMER, acme);
        admin = user(ADMIN_ID, UserRole.ORG_ADMIN, acme);
        agent = user(AGENT_ID, UserRole.SUPPORT_AGENT, acme);

        ticket = new Ticket();
        ticket.setId(TICKET_ID);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setCustomer(customer);
        ticket.setAssignedAgent(agent);
        ticket.setOrganization(acme);
    }

    private static Organization organization(long id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName("Org " + id);
        return organization;
    }

    private static User user(long id, UserRole role, Organization organization) {
        User user = new User();
        user.setId(id);
        user.setName(role + " " + id);
        user.setEmail("user" + id + "@example.test");
        user.setPassword("never-returned");
        user.setRole(role);
        user.setActive(true);
        user.setOrganization(organization);
        return user;
    }

    private void given(User actor) {
        when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
        when(userService.findOrThrow(actor.getId())).thenReturn(actor);
    }

    private void stubSave() {
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId(99L);
            return message;
        });
    }

    @Nested
    @DisplayName("Posting")
    class Posting {

        @Test
        @DisplayName("the customer posts; the message is stored against the ticket with the clock's time")
        void customerPosts() {
            given(customer);
            stubSave();

            MessageResponse response = messageService.postMessage(TICKET_ID, new PostMessageRequest("Still jamming"), CUSTOMER_ID);

            ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(saved.capture());
            assertThat(saved.getValue().getTicket()).isSameAs(ticket);
            assertThat(saved.getValue().getSender()).isSameAs(customer);

            assertThat(response.id()).isEqualTo(99L);
            assertThat(response.ticketId()).isEqualTo(TICKET_ID);
            assertThat(response.sender().id()).isEqualTo(CUSTOMER_ID);
            assertThat(response.sender().role()).isEqualTo(UserRole.CUSTOMER);
            assertThat(response.content()).isEqualTo("Still jamming");
            assertThat(response.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("the assigned agent posts")
        void assignedAgentPosts() {
            given(agent);
            stubSave();

            MessageResponse response = messageService.postMessage(TICKET_ID, new PostMessageRequest("Try a new cartridge"), AGENT_ID);

            assertThat(response.sender().id()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("surrounding whitespace is trimmed before storing")
        void contentIsStripped() {
            given(customer);
            stubSave();

            MessageResponse response = messageService.postMessage(TICKET_ID, new PostMessageRequest("  \n Hello \t "), CUSTOMER_ID);

            assertThat(response.content()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("the customer can add details to an OPEN ticket before any agent is assigned")
        void customerPostsOnUnassignedTicket() {
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setAssignedAgent(null);
            given(customer);
            stubSave();

            assertThat(messageService.postMessage(TICKET_ID, new PostMessageRequest("More info"), CUSTOMER_ID).content())
                    .isEqualTo("More info");
        }

        @ParameterizedTest(name = "posting is allowed on a {0} ticket")
        @EnumSource(value = TicketStatus.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
        void postingAllowedUnlessClosed(TicketStatus status) {
            ticket.setStatus(status);
            given(customer);
            stubSave();

            assertThat(messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), CUSTOMER_ID)).isNotNull();
        }

        @Test
        @DisplayName("posting on a CLOSED ticket is a 409 telling the customer to reopen")
        void rejectsClosedTicket() {
            ticket.setStatus(TicketStatus.CLOSED);
            given(customer);

            assertThatThrownBy(() -> messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), CUSTOMER_ID))
                    .isInstanceOf(InvalidTicketStateException.class)
                    .hasMessage("Cannot post a message on a CLOSED ticket; reopen it first");

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("an org admin can read but not post")
        void orgAdminCannotPost() {
            given(admin);

            assertThatThrownBy(() -> messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), ADMIN_ID))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Only the ticket's customer and its assigned agent can post messages");

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("an agent from the same organization who is not assigned cannot post")
        void otherAgentCannotPost() {
            User otherAgent = user(21L, UserRole.SUPPORT_AGENT, acme);
            given(otherAgent);

            assertThatThrownBy(() -> messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), 21L))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("another customer from the same organization cannot post")
        void otherCustomerCannotPost() {
            User otherCustomer = user(2L, UserRole.CUSTOMER, acme);
            given(otherCustomer);

            assertThatThrownBy(() -> messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), 2L))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        @DisplayName("an inactive customer cannot post")
        void inactiveCustomerCannotPost() {
            customer.setActive(false);
            given(customer);

            assertThatThrownBy(() -> messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), CUSTOMER_ID))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot post messages");

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("unknown ticket and unknown sender are 404s")
        void unknownIds() {
            when(ticketService.findOrThrow(TICKET_ID)).thenThrow(new TicketNotFoundException(TICKET_ID));
            assertThatThrownBy(() -> messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), CUSTOMER_ID))
                    .isInstanceOf(TicketNotFoundException.class);

            org.mockito.Mockito.reset(ticketService);
            when(ticketService.findOrThrow(TICKET_ID)).thenReturn(ticket);
            when(userService.findOrThrow(CUSTOMER_ID)).thenThrow(new UserNotFoundException(CUSTOMER_ID));
            assertThatThrownBy(() -> messageService.postMessage(TICKET_ID, new PostMessageRequest("Hi"), CUSTOMER_ID))
                    .isInstanceOf(UserNotFoundException.class);

            verify(messageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        private List<Message> thread() {
            Message first = new Message();
            first.setId(1L);
            first.setTicket(ticket);
            first.setSender(customer);
            first.setContent("Printer jams");
            first.setCreatedAt(NOW.minusMinutes(10));

            Message second = new Message();
            second.setId(2L);
            second.setTicket(ticket);
            second.setSender(agent);
            second.setContent("On it");
            second.setCreatedAt(NOW.minusMinutes(5));
            return List.of(first, second);
        }

        @ParameterizedTest(name = "the ticket''s {0} can read the thread")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT", "ORG_ADMIN"})
        void participantsAndAdminRead(UserRole role) {
            User reader = switch (role) {
                case CUSTOMER -> customer;
                case SUPPORT_AGENT -> agent;
                default -> admin;
            };
            given(reader);
            when(messageRepository.findThread(TICKET_ID)).thenReturn(thread());

            List<MessageResponse> messages = messageService.getMessages(TICKET_ID, reader.getId());

            assertThat(messages).extracting(MessageResponse::content).containsExactly("Printer jams", "On it");
            assertThat(messages).extracting(m -> m.sender().role())
                    .containsExactly(UserRole.CUSTOMER, UserRole.SUPPORT_AGENT);
        }

        @Test
        @DisplayName("a message whose sender was deleted is returned with a null sender")
        void deletedSenderIsNull() {
            given(customer);
            Message orphan = thread().get(0);
            orphan.setSender(null);
            when(messageRepository.findThread(TICKET_ID)).thenReturn(List.of(orphan));

            assertThat(messageService.getMessages(TICKET_ID, CUSTOMER_ID).get(0).sender()).isNull();
        }

        @Test
        @DisplayName("a CLOSED ticket's thread stays readable")
        void closedTicketReadable() {
            ticket.setStatus(TicketStatus.CLOSED);
            given(customer);
            when(messageRepository.findThread(TICKET_ID)).thenReturn(thread());

            assertThat(messageService.getMessages(TICKET_ID, CUSTOMER_ID)).hasSize(2);
        }

        @Test
        @DisplayName("an admin from another organization cannot read")
        void crossOrganizationAdminCannotRead() {
            User globexAdmin = user(11L, UserRole.ORG_ADMIN, globex);
            given(globexAdmin);

            assertThatThrownBy(() -> messageService.getMessages(TICKET_ID, 11L))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageStartingWith("Only the ticket's customer, its assigned agent and administrators");

            verify(messageRepository, never()).findThread(anyLong());
        }

        @ParameterizedTest(name = "an unrelated {0} in the same organization cannot read")
        @EnumSource(value = UserRole.class, names = {"CUSTOMER", "SUPPORT_AGENT", "SUPER_ADMIN"})
        void unrelatedUsersCannotRead(UserRole role) {
            User stranger = user(30L, role, acme);
            given(stranger);

            assertThatThrownBy(() -> messageService.getMessages(TICKET_ID, 30L))
                    .isInstanceOf(ForbiddenOperationException.class);

            verify(messageRepository, never()).findThread(anyLong());
        }

        @Test
        @DisplayName("an inactive admin cannot read")
        void inactiveAdminCannotRead() {
            admin.setActive(false);
            given(admin);

            assertThatThrownBy(() -> messageService.getMessages(TICKET_ID, ADMIN_ID))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Inactive users cannot read messages");
        }
    }
}
