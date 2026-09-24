package com.ibrahim.helpdesk.security.reset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);

    /** Housekeeping: links that can no longer be used are of no further interest. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
