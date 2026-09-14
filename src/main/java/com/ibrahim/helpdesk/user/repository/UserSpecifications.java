package com.ibrahim.helpdesk.user.repository;

import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Query predicates for user lists. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /**
     * @param organizationId users of this organization; null for every organization
     * @param role           users with this role; null for any
     * @param active         only active or only inactive users; null for both
     * @param query          case-insensitive text found in the name or email; null for none
     */
    public static Specification<User> matching(Long organizationId, UserRole role, Boolean active, String query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organization").get("id"), organizationId));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (query != null) {
                String pattern = PageRequests.containsPattern(query);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
