package com.ibrahim.helpdesk.ticket.priority;

import com.ibrahim.helpdesk.ticket.entity.TicketCategory;

/**
 * Everything a priority decision may depend on. Kept separate from the Ticket
 * entity so a policy cannot depend on anything else, which keeps priority a
 * pure, repeatable function of these values.
 */
public record PriorityInput(
        TicketCategory category,
        String title,
        String description,
        int reopenCount
) {
}
