package com.ibrahim.helpdesk.security.auth;

import com.ibrahim.helpdesk.exception.TooManyLoginAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing. After too many wrong passwords for one email
 * within the window, sign-in for that email is refused, without checking the
 * password, until the window has passed. A correct password clears the count.
 *
 * <p>Counting per email stops guessing against one account from many
 * addresses. The trade-off is that someone who knows an email can block that
 * account from signing in for one window at a time.
 *
 * <p>State is kept in memory, so it applies per running instance and resets
 * on restart. That is sufficient for a single instance; several instances
 * would need a shared store.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    /** Above this many tracked emails, expired entries are purged. */
    private static final int PURGE_THRESHOLD = 10_000;

    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    private record Attempts(int failures, Instant firstFailure, Instant blockedUntil) {
    }

    /** Throws if sign-in for this email is currently blocked. */
    public void checkAllowed(String email) {
        Attempts current = attempts.get(key(email));
        Instant now = clock.instant();
        if (current != null && current.blockedUntil() != null && now.isBefore(current.blockedUntil())) {
            throw new TooManyLoginAttemptsException(Duration.between(now, current.blockedUntil()));
        }
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        attempts.compute(key(email), (ignored, current) -> {
            boolean expired = current == null
                    || (current.blockedUntil() != null && !now.isBefore(current.blockedUntil()))
                    || (current.blockedUntil() == null && !now.isBefore(current.firstFailure().plus(properties.window())));
            int failures = expired ? 1 : current.failures() + 1;
            Instant first = expired ? now : current.firstFailure();
            Instant blockedUntil = failures >= properties.maxFailures() ? now.plus(properties.window()) : null;
            return new Attempts(failures, first, blockedUntil);
        });
        if (attempts.size() > PURGE_THRESHOLD) {
            purgeExpired(now);
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private void purgeExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            Attempts value = entry.getValue();
            Instant end = value.blockedUntil() != null ? value.blockedUntil() : value.firstFailure().plus(properties.window());
            return !now.isBefore(end);
        });
    }

    private static String key(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
