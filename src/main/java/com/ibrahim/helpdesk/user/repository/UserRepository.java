package com.ibrahim.helpdesk.user.repository;

import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository
extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(UserRole role);

    Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);

    /** A page of users, with each user's organization fetched in the same query. */
    @Override
    @EntityGraph(attributePaths = "organization")
    Page<User> findAll(Specification<User> specification, Pageable pageable);

    /**
     * Users whose stored password has no {@code {algorithm}} prefix, i.e. was
     * saved as plain text before passwords were hashed.
     */
    @Query("select u from User u where u.password is not null and u.password not like '{%'")
    List<User> findWithUnhashedPasswords();
}
