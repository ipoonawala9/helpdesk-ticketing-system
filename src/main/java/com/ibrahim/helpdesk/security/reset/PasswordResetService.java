package com.ibrahim.helpdesk.security.reset;

import com.ibrahim.helpdesk.exception.FieldValidationException;
import com.ibrahim.helpdesk.security.auth.LoginAttemptLimiter;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Self-service password reset.
 *
 * <p>Requesting a link always looks the same from outside, whether or not the
 * address has an account, so the endpoint cannot be used to discover who is
 * registered. Links are single-use, expire, and are stored only as a hash.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetLinkSender linkSender;
    private final PasswordResetProperties properties;
    private final LoginAttemptLimiter limiter;
    private final Clock clock;
    private final FrontendProperties frontend;

    /** Sends a reset link if the address belongs to an active account. Silent either way. */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.strip().toLowerCase(Locale.ROOT);

        // Counted per address, like sign-in, so this cannot be used to flood an inbox.
        String limiterKey = "password-reset:" + email;
        limiter.checkAllowed(limiterKey);
        limiter.recordFailure(limiterKey, properties.maxRequests(), properties.validFor());

        Optional<User> account = userRepository.findByEmailIgnoreCase(email)
                .filter(user -> Boolean.TRUE.equals(user.getActive()));
        if (account.isEmpty()) {
            log.info("Password reset requested for an address with no active account");
            return;
        }
        User user = account.get();

        // Only the newest link stays valid.
        tokenRepository.deleteAllForUser(user.getId());

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Instant now = clock.instant();
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setTokenHash(hash(token));
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(properties.validFor()));
        tokenRepository.save(entity);

        linkSender.send(user, frontend.resetLink(token));
    }

    /** Sets a new password if the link is valid, and uses the link up. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();

        PasswordResetToken entity = tokenRepository.findByTokenHash(hash(token == null ? "" : token))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new FieldValidationException("token",
                        "This reset link is no longer valid. Ask for a new one."));

        User user = entity.getUser();
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new FieldValidationException("token", "This reset link is no longer valid. Ask for a new one.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        entity.setUsedAt(now);
        tokenRepository.save(entity);

        // A completed reset clears any sign-in block for that address.
        limiter.recordSuccess(user.getEmail());
        limiter.recordSuccess("password-reset:" + user.getEmail());
        log.info("Password reset completed for user {}", user.getId());
    }

    /** Removes links that have expired. Safe to call at any time. */
    @Transactional
    public int deleteExpiredTokens() {
        return tokenRepository.deleteExpired(clock.instant());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
