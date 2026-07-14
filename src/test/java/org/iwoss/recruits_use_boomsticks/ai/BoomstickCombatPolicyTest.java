package org.iwoss.recruits_use_boomsticks.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoomstickCombatPolicyTest {
    @Test
    void disabledCompatibilityNeverSuppressesTheOriginalCrossbowGoal() {
        assertFalse(BoomstickCombatPolicy.shouldSuppressOriginalGoal(false, true));
    }

    @Test
    void enabledCompatibilitySuppressesTheOriginalGoalForSupportedWeapons() {
        assertTrue(BoomstickCombatPolicy.shouldSuppressOriginalGoal(true, true));
        assertFalse(BoomstickCombatPolicy.shouldSuppressOriginalGoal(true, false));
    }

    @Test
    void strategicFireNeverReplacesAValidCombatTarget() {
        assertFalse(BoomstickCombatPolicy.shouldUseStrategicFire(true, true));
        assertTrue(BoomstickCombatPolicy.shouldUseStrategicFire(false, true));
        assertFalse(BoomstickCombatPolicy.shouldUseStrategicFire(false, false));
    }

    @Test
    void targetWithoutLineOfSightMustBeApproached() {
        assertTrue(BoomstickCombatPolicy.shouldApproachTarget(false, 4.0D, 45.0D));
    }

    @Test
    void visibleDistantTargetMustBeApproached() {
        assertTrue(BoomstickCombatPolicy.shouldApproachTarget(true, 46.0D * 46.0D, 45.0D));
    }

    @Test
    void visibleTargetInsideCombatRangeDoesNotNeedApproach() {
        assertFalse(BoomstickCombatPolicy.shouldApproachTarget(true, 20.0D * 20.0D, 45.0D));
    }

    @Test
    void onlyTargetsInsideTheMaximumRangeCanBeAimedAt() {
        assertTrue(BoomstickCombatPolicy.isWithinCombatRange(45.0D * 45.0D, 45.0D));
        assertFalse(BoomstickCombatPolicy.isWithinCombatRange(46.0D * 46.0D, 45.0D));
        assertFalse(BoomstickCombatPolicy.isWithinCombatRange(Double.NaN, 45.0D));
    }

    @Test
    void mountedReloadMatchesTheSlowerUpstreamMusketBehavior() {
        assertEquals(40, BoomstickCombatPolicy.reloadTicks(40, false, false));
        assertEquals(80, BoomstickCombatPolicy.reloadTicks(40, true, false));
        assertEquals(2, BoomstickCombatPolicy.reloadTicks(0, true, false));
        assertEquals(Integer.MAX_VALUE, BoomstickCombatPolicy.reloadTicks(Integer.MAX_VALUE, true, false));
    }

    @Test
    void arbalestUsesTheShorterVanillaCrossbowPace() {
        assertEquals(25, BoomstickCombatPolicy.reloadTicks(50, false, true));
        assertEquals(50, BoomstickCombatPolicy.reloadTicks(50, true, true));
    }

    @Test
    void cooldownOnlySeparatesTheShotFromReload() {
        assertEquals(5, BoomstickCombatPolicy.cooldownTicks());
    }

    @Test
    void misfireProbabilityUsesOneRollPerProjectile() {
        assertTrue(BoomstickCombatPolicy.isMisfire(true, 0.24F, 25));
        assertFalse(BoomstickCombatPolicy.isMisfire(true, 0.25F, 25));
        assertFalse(BoomstickCombatPolicy.isMisfire(false, 0.0F, 100));
    }
}
