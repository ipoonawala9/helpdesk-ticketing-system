package com.ibrahim.helpdesk.ticket.repository;

import com.ibrahim.helpdesk.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository
    extends JpaRepository<Ticket, Long> {
}
