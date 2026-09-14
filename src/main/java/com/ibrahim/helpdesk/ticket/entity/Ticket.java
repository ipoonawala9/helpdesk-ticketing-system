package com.ibrahim.helpdesk.ticket.entity;
import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
@Entity
@Table(
        name = "tickets",
        // Each index serves one role's scoped ticket list, which filters on the
        // first column and orders by the second.
        indexes = {
                @Index(name = "idx_tickets_organization_created_at", columnList = "organization_id, created_at"),
                @Index(name = "idx_tickets_customer_created_at", columnList = "customer_id, created_at"),
                @Index(name = "idx_tickets_assigned_agent_created_at", columnList = "assigned_agent_id, created_at"),
                @Index(name = "idx_tickets_status", columnList = "status")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_tickets_ticket_number", columnNames = "ticket_number")
)
@Getter
@Setter
@NoArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null only between the first and second save of a new ticket; unique once set. */
    @Column(name = "ticket_number", length = 20)
    private String ticketNumber;

    // Lengths match the Bean Validation limits on the ticket request DTOs.
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    /**
     * Nullable. If the agent's account is deleted the ticket is kept and simply
     * becomes unassigned.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User assignedAgent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private Integer reopenCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    /**
     * Priority as a number, LOW = 0 to CRITICAL = 3, so sorting by priority
     * follows severity rather than the alphabetical order of the stored names.
     * Read-only: computed by the database on every read.
     */
    @Formula("case priority when 'LOW' then 0 when 'MEDIUM' then 1 when 'HIGH' then 2 when 'CRITICAL' then 3 end")
    @Setter(AccessLevel.NONE)
    private Integer priorityRank;

    /** Status as a number in lifecycle order, OPEN = 0 to CLOSED = 5, for sorting. Read-only. */
    @Formula("case status when 'OPEN' then 0 when 'ASSIGNED' then 1 when 'IN_PROGRESS' then 2 "
            + "when 'REOPENED' then 3 when 'RESOLVED' then 4 when 'CLOSED' then 5 end")
    @Setter(AccessLevel.NONE)
    private Integer statusRank;
}
