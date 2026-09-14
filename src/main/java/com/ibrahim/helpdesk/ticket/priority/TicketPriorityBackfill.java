package com.ibrahim.helpdesk.ticket.priority;

import com.ibrahim.helpdesk.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, gives a priority to any ticket that has none, so tickets created
 * before priority was automatic are not left without one.
 */
@Component
@RequiredArgsConstructor
public class TicketPriorityBackfill {

    private static final Logger log = LoggerFactory.getLogger(TicketPriorityBackfill.class);

    private final TicketService ticketService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        int updated = ticketService.backfillMissingPriorities();
        if (updated > 0) {
            log.info("Assigned a priority to {} existing ticket(s) that had none", updated);
        }
    }
}
