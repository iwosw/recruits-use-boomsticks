package org.iwoss.recruits_use_boomsticks.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoomstickReloadProgressTest {
    private static final int ENTITY_ID = 7;

    @AfterEach
    void forgetClocks() {
        BoomstickReloadProgress.clearAll();
    }

    @Test
    void firstFrameStartsThePull() {
        assertEquals(0.0F, BoomstickReloadProgress.progress(ENTITY_ID, 120.0F, 20));
    }

    @Test
    void thePullRunsAtTheWeaponsOwnCockingSpeed() {
        BoomstickReloadProgress.progress(ENTITY_ID, 100.0F, 20);

        assertEquals(0.5F, BoomstickReloadProgress.progress(ENTITY_ID, 110.0F, 20));
        assertEquals(0.75F, BoomstickReloadProgress.progress(ENTITY_ID, 115.0F, 20));
    }

    @Test
    void thePullRepeatsForAsLongAsTheMarkerStands() {
        BoomstickReloadProgress.progress(ENTITY_ID, 100.0F, 20);

        assertEquals(0.0F, BoomstickReloadProgress.progress(ENTITY_ID, 120.0F, 20));
        assertEquals(0.25F, BoomstickReloadProgress.progress(ENTITY_ID, 125.0F, 20));
    }

    @Test
    void aClearedClockStartsTheNextReloadFromZero() {
        BoomstickReloadProgress.progress(ENTITY_ID, 100.0F, 20);
        BoomstickReloadProgress.clear(ENTITY_ID);

        assertEquals(0.0F, BoomstickReloadProgress.progress(ENTITY_ID, 137.0F, 20));
    }

    @Test
    void anIdHandedToANewerEntityRestartsRatherThanRunningBackwards() {
        BoomstickReloadProgress.progress(ENTITY_ID, 500.0F, 20);

        assertEquals(0.0F, BoomstickReloadProgress.progress(ENTITY_ID, 4.0F, 20));
        assertEquals(0.5F, BoomstickReloadProgress.progress(ENTITY_ID, 14.0F, 20));
    }

    @Test
    void aWeaponWithoutAReloadWindowStillYieldsAUsableFraction() {
        float progress = BoomstickReloadProgress.progress(ENTITY_ID, 3.5F, 0);

        assertTrue(progress >= 0.0F && progress < 1.0F, "progress out of range: " + progress);
    }
}
