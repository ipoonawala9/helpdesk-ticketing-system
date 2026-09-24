package com.ibrahim.helpdesk.security.reset;

import com.ibrahim.helpdesk.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A single-use link for setting a new password.
 *
 * <p>Only the SHA-256 of the token is stored: anyone reading the database
 * cannot use a row to take over an account, in the same way password hashes
 * cannot be replayed.
 */
@Entity
@Table(
        name = "password_reset_tokens",
        indexes = @Index(name = "idx_password_reset_tokens_user", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_password_reset_tokens_hash", columnNames = "token_hash")
)
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set the moment the link is used, so it cannot be used twice. */
    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
