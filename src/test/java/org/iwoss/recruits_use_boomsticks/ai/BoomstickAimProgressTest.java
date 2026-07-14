package org.iwoss.recruits_use_boomsticks.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoomstickAimProgressTest {
    @Test
    void changingAimIdentityRestartsTheFullAimWindow() {
        BoomstickAimProgress progress = new BoomstickAimProgress(2);

        assertFalse(progress.advance("target-a"));
        assertTrue(progress.advance("target-a"));

        assertFalse(progress.advance("target-b"));
        assertTrue(progress.advance("target-b"));
    }

    @Test
    void losingAimPointClearsAccumulatedProgress() {
        BoomstickAimProgress progress = new BoomstickAimProgress(2);

        assertFalse(progress.advance("target"));
        assertFalse(progress.advance(null));
        assertFalse(progress.advance("target"));
        assertTrue(progress.advance("target"));
    }

    @Test
    void zeroLengthAimWindowCompletesOnTheFirstAimTick() {
        BoomstickAimProgress progress = new BoomstickAimProgress(0);

        assertTrue(progress.advance("target"));
    }
}