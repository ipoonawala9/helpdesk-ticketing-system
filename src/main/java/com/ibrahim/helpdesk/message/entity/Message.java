package com.ibrahim.helpdesk.message.entity;

import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * One message in a ticket's conversation between the customer and the
 * assigned agent.
 */
@Entity
@Table(
        name = "messages",
        indexes = @Index(name = "idx_messages_ticket_created_at", columnList = "ticket_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Messages have no meaning without their ticket, so they are deleted with it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Ticket ticket;

    /**
     * Nullable so that, as with a ticket's assigned agent, deleting a user
     * keeps the conversation and only removes the link to its author.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User sender;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
