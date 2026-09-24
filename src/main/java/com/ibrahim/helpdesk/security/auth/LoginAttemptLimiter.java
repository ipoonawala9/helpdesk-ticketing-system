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

    /** Throws if attempts for this key are currently blocked. */
    public void checkAllowed(String key) {
        Attempts current = attempts.get(normalise(key));
        Instant now = clock.instant();
        if (current != null && current.blockedUntil() != null && now.isBefore(current.blockedUntil())) {
            throw new TooManyLoginAttemptsException(Duration.between(now, current.blockedUntil()));
        }
    }

    public void recordFailure(String key) {
        recordFailure(key, properties.maxFailures(), properties.window());
    }

    /**
     * Counts an attempt against limits of the caller's choosing, for actions
     * that are not sign-in, such as asking for a password reset link.
     */
    public void recordFailure(String key, int maxFailures, Duration window) {
        Instant now = clock.instant();
        attempts.compute(normalise(key), (ignored, current) -> {
            boolean expired = current == null
                    || (current.blockedUntil() != null && !now.isBefore(current.blockedUntil()))
                    || (current.blockedUntil() == null && !now.isBefore(current.firstFailure().plus(window)));
            int failures = expired ? 1 : current.failures() + 1;
            Instant first = expired ? now : current.firstFailure();
            Instant blockedUntil = failures >= maxFailures ? now.plus(window) : null;
            return new Attempts(failures, first, blockedUntil);
        });
        if (attempts.size() > PURGE_THRESHOLD) {
            purgeExpired(now);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(normalise(key));
    }

    private void purgeExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            Attempts value = entry.getValue();
            Instant end = value.blockedUntil() != null ? value.blockedUntil() : value.firstFailure().plus(properties.window());
            return !now.isBefore(end);
        });
    }

    private static String normalise(String key) {
        return key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
    }
}
