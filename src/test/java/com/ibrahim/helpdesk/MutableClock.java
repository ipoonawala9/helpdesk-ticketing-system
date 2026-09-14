package com.ibrahim.helpdesk;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock for end-to-end tests that follows real time until a test moves it
 * forward, after which it stays frozen at the advanced instant.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant frozenAt;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void advance(Duration duration) {
        frozenAt = instant().plus(duration);
    }

    /** Returns to following real time. */
    public void reset() {
        frozenAt = null;
    }

    @Override
    public Instant instant() {
        Instant frozen = frozenAt;
        return frozen != null ? frozen : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock copy = new MutableClock(zone);
        copy.frozenAt = frozenAt;
        return copy;
    }
}
