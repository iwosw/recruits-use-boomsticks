package org.iwoss.recruits_use_boomsticks.ai;

import java.util.Objects;

/** Tracks uninterrupted aiming time for one logical target or strategic position. */
final class BoomstickAimProgress {
    private final int requiredTicks;
    private Object identity;
    private int ticks;

    BoomstickAimProgress(int requiredTicks) {
        if (requiredTicks < 0) {
            throw new IllegalArgumentException("requiredTicks must be non-negative");
        }
        this.requiredTicks = requiredTicks;
    }

    boolean advance(Object nextIdentity) {
        if (nextIdentity == null) {
            reset();
            return false;
        }
        if (!Objects.equals(identity, nextIdentity)) {
            identity = nextIdentity;
            ticks = 0;
        }
        ticks++;
        return ticks >= requiredTicks;
    }

    void reset() {
        identity = null;
        ticks = 0;
    }
}
