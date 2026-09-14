package com.ibrahim.helpdesk.ticket.repository;

import com.ibrahim.helpdesk.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped lookups carry the scope in the query itself, so a ticket
 * outside the caller's scope is never loaded at all.
 */
public interface TicketRepository
    extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Tickets created before priority was calculated automatically. */
    List<Ticket> findByPriorityIsNull();

    Optional<Ticket> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Ticket> findByIdAndAssignedAgentId(Long id, Long assignedAgentId);

    Optional<Ticket> findByIdAndOrganizationId(Long id, Long organizationId);

    /**
     * A page of tickets. The customer, agent and organization are fetched in
     * the same query, since every ticket response includes them.
     */
    @Override
    @EntityGraph(attributePaths = {"customer", "assignedAgent", "organization"})
    Page<Ticket> findAll(Specification<Ticket> specification, Pageable pageable);
}
