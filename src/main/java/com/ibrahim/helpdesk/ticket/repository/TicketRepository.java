package com.ibrahim.helpdesk.ticket.repository;

import com.ibrahim.helpdesk.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository
    extends JpaRepository<Ticket, Long> {

    /** Tickets created before priority was calculated automatically. */
    List<Ticket> findByPriorityIsNull();
}
