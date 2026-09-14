package com.ibrahim.helpdesk.ticket.service;

import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;

import java.util.Objects;

/**
 * The single definition of how a user relates to a ticket. Workflow actions
 * and ticket messaging both authorise against these, so the two can never
 * disagree about who counts as the customer or the assigned agent.
 */
public final class TicketParticipants {

    private TicketParticipants() {
    }

    /** The CUSTOMER who opened the ticket. */
    public static boolean isCustomer(Ticket ticket, User user) {
        return user.getRole() == UserRole.CUSTOMER
                && ticket.getCustomer() != null
                && Objects.equals(ticket.getCustomer().getId(), user.getId());
    }

    /** The SUPPORT_AGENT currently holding the ticket. A previously assigned agent does not count. */
    public static boolean isAssignedAgent(Ticket ticket, User user) {
        return user.getRole() == UserRole.SUPPORT_AGENT
                && ticket.getAssignedAgent() != null
                && Objects.equals(ticket.getAssignedAgent().getId(), user.getId());
    }

    /** An ORG_ADMIN of the organization the ticket belongs to. */
    public static boolean isOrgAdmin(Ticket ticket, User user) {
        return user.getRole() == UserRole.ORG_ADMIN
                && belongsTo(user, ticket.getOrganization());
    }

    public static boolean belongsTo(User user, Organization organization) {
        return user.getOrganization() != null
                && organization != null
                && Objects.equals(user.getOrganization().getId(), organization.getId());
    }
}
