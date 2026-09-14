package com.ibrahim.helpdesk.ticket.priority;

import com.ibrahim.helpdesk.ticket.entity.TicketPriority;

/**
 * Decides a ticket's priority. Priority is always server-determined; clients
 * never supply it.
 *
 * <p>Implementations must be deterministic: the same input always yields the
 * same priority. To change the rules, provide a different implementation as the
 * primary bean; callers depend only on this interface.
 */
public interface TicketPriorityPolicy {

    TicketPriority determine(PriorityInput input);
}
