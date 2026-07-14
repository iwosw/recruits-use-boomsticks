package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoomstickProjectilePolicyTest {
    @Test
    void disabledCompatibilityLeavesEveryProjectileHookInactive() {
        assertFalse(BoomstickProjectilePolicy.shouldApply(false, true, true));
        assertFalse(BoomstickProjectilePolicy.shouldApply(true, false, true));
        assertFalse(BoomstickProjectilePolicy.shouldApply(true, true, false));
        assertTrue(BoomstickProjectilePolicy.shouldApply(true, true, true));
    }

    @Test
    void collectibleProjectilesUseVanillaDespawnTimingAfterLanding() {
        assertFalse(BoomstickProjectilePolicy.shouldDiscard(true, true, 200, 200));
        assertFalse(BoomstickProjectilePolicy.shouldDiscard(true, true, 1_200, 200));
    }

    @Test
    void collectibleProjectilesAreDiscardedAtTheCompatibilityLimitWhileAirborne() {
        assertFalse(BoomstickProjectilePolicy.shouldDiscard(true, false, 199, 200));
        assertTrue(BoomstickProjectilePolicy.shouldDiscard(true, false, 200, 200));
    }

    @Test
    void nonCollectibleProjectilesAreDiscardedAtTheCompatibilityLimit() {
        assertFalse(BoomstickProjectilePolicy.shouldDiscard(false, false, 199, 200));
        assertTrue(BoomstickProjectilePolicy.shouldDiscard(false, false, 200, 200));
    }
}
