package com.ibrahim.helpdesk.ticket.repository;

import com.ibrahim.helpdesk.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped lookups carry the scope in the query itself, so a ticket
 * outside the caller's scope is never loaded at all. Lists fetch the customer,
 * agent and organization in the same query, since every ticket response
 * includes them.
 */
public interface TicketRepository
    extends JpaRepository<Ticket, Long> {

    /** Tickets created before priority was calculated automatically. */
    List<Ticket> findByPriorityIsNull();

    Optional<Ticket> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Ticket> findByIdAndAssignedAgentId(Long id, Long assignedAgentId);

    Optional<Ticket> findByIdAndOrganizationId(Long id, Long organizationId);

    @EntityGraph(attributePaths = {"customer", "assignedAgent", "organization"})
    List<Ticket> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "assignedAgent", "organization"})
    List<Ticket> findByAssignedAgentIdOrderByCreatedAtDescIdDesc(Long assignedAgentId);

    @EntityGraph(attributePaths = {"customer", "assignedAgent", "organization"})
    List<Ticket> findByOrganizationIdOrderByCreatedAtDescIdDesc(Long organizationId);

    @EntityGraph(attributePaths = {"customer", "assignedAgent", "organization"})
    List<Ticket> findAllByOrderByCreatedAtDescIdDesc();
}
