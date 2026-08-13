package org.iwoss.recruits_use_boomsticks.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoomstickCombatPolicyTest {
    @Test
    void disabledCompatibilityYieldsForHeldAndInventoryWeapons() {
        assertFalse(BoomstickCombatPolicy.shouldSuppressOriginalGoal(false, true, false));
        assertFalse(BoomstickCombatPolicy.shouldSuppressOriginalGoal(false, false, true));
    }

    @Test
    void enabledCompatibilitySuppressesTheOriginalGoalForSupportedWeapons() {
        assertTrue(BoomstickCombatPolicy.shouldSuppressOriginalGoal(true, true, false));
        assertTrue(BoomstickCombatPolicy.shouldSuppressOriginalGoal(true, false, true));
        assertFalse(BoomstickCombatPolicy.shouldSuppressOriginalGoal(true, false, false));
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
    void aWeaponMayOnlyShortenTheSharedCombatRange() {
        assertEquals(13.4D, BoomstickCombatPolicy.clampCombatRange(13.4D, 45.0D));
        assertEquals(45.0D, BoomstickCombatPolicy.clampCombatRange(62.6D, 45.0D));
        assertEquals(45.0D, BoomstickCombatPolicy.clampCombatRange(45.0D, 45.0D));
    }

    @Test
    void anUnusableWeaponRangeFallsBackToTheSharedMaximum() {
        assertEquals(45.0D, BoomstickCombatPolicy.clampCombatRange(0.0D, 45.0D));
        assertEquals(45.0D, BoomstickCombatPolicy.clampCombatRange(-1.0D, 45.0D));
        assertEquals(45.0D, BoomstickCombatPolicy.clampCombatRange(Double.NaN, 45.0D));
        assertEquals(0.0D, BoomstickCombatPolicy.clampCombatRange(13.4D, Double.NaN));
    }

    @Test
    void aShortRangedWeaponMakesADistantTargetWorthApproaching() {
        // A thrown cobblestone runs out of drop compensation at roughly thirteen blocks, so a target
        // twenty blocks away is out of reach for it while a full-range weapon would already shoot.
        assertTrue(BoomstickCombatPolicy.shouldApproachTarget(true, 20.0D * 20.0D, 13.4D));
        assertFalse(BoomstickCombatPolicy.shouldApproachTarget(true, 20.0D * 20.0D, 45.0D));
        assertFalse(BoomstickCombatPolicy.isWithinCombatRange(20.0D * 20.0D, 13.4D));
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
