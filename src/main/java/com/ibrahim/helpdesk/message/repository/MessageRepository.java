package com.ibrahim.helpdesk.message.repository;

import com.ibrahim.helpdesk.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * A ticket's conversation, oldest first. Senders are fetched in the same
     * query so mapping the thread does not issue one query per message. The id
     * breaks ties between messages created in the same instant.
     */
    @Query("""
            select m from Message m
            left join fetch m.sender
            where m.ticket.id = :ticketId
            order by m.createdAt asc, m.id asc
            """)
    List<Message> findThread(@Param("ticketId") Long ticketId);
}
