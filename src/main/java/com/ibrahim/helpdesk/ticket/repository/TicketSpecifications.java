package com.ibrahim.helpdesk.ticket.repository;

import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.ticket.dto.TicketSearchCriteria;
import com.ibrahim.helpdesk.ticket.entity.Ticket;
import com.ibrahim.helpdesk.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Query predicates for ticket lists. */
public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    /**
     * The tickets a user may see: a customer's own, an agent's assigned, an
     * administrator's organization's, or all for a SUPER_ADMIN. Always the
     * first predicate of any user-facing ticket list.
     */
    public static Specification<Ticket> visibleTo(User viewer) {
        return (root, query, cb) -> switch (viewer.getRole()) {
            case SUPER_ADMIN -> cb.conjunction();
            case ORG_ADMIN -> viewer.getOrganization() == null
                    ? cb.disjunction()
                    : cb.equal(root.get("organization").get("id"), viewer.getOrganization().getId());
            case SUPPORT_AGENT -> cb.equal(root.get("assignedAgent").get("id"), viewer.getId());
            case CUSTOMER -> cb.equal(root.get("customer").get("id"), viewer.getId());
        };
    }

    public static Specification<Ticket> matching(TicketSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
                predicates.add(root.get("status").in(criteria.statuses()));
            }
            if (criteria.priorities() != null && !criteria.priorities().isEmpty()) {
                predicates.add(root.get("priority").in(criteria.priorities()));
            }
            if (criteria.categories() != null && !criteria.categories().isEmpty()) {
                predicates.add(root.get("category").in(criteria.categories()));
            }
            if (criteria.customerId() != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), criteria.customerId()));
            }
            if (criteria.assignedAgentId() != null) {
                predicates.add(cb.equal(root.get("assignedAgent").get("id"), criteria.assignedAgentId()));
            }
            if (Boolean.TRUE.equals(criteria.unassigned())) {
                predicates.add(cb.isNull(root.get("assignedAgent")));
            }
            if (criteria.organizationId() != null) {
                predicates.add(cb.equal(root.get("organization").get("id"), criteria.organizationId()));
            }
            if (criteria.query() != null) {
                String pattern = PageRequests.containsPattern(criteria.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ticketNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
