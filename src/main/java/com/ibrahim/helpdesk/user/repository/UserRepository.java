package com.ibrahim.helpdesk.user.repository;

import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository
extends JpaRepository<User, Long>{

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(UserRole role);

    Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);

    // User lists fetch the organization in the same query instead of one query per organization.

    @EntityGraph(attributePaths = "organization")
    List<User> findAllByOrderByNameAscIdAsc();

    @EntityGraph(attributePaths = "organization")
    List<User> findByRoleOrderByNameAscIdAsc(UserRole role);

    @EntityGraph(attributePaths = "organization")
    List<User> findByOrganizationIdOrderByNameAscIdAsc(Long organizationId);

    @EntityGraph(attributePaths = "organization")
    List<User> findByOrganizationIdAndRoleOrderByNameAscIdAsc(Long organizationId, UserRole role);

    /**
     * Users whose stored password has no {@code {algorithm}} prefix, i.e. was
     * saved as plain text before passwords were hashed.
     */
    @Query("select u from User u where u.password is not null and u.password not like '{%'")
    List<User> findWithUnhashedPasswords();
}
