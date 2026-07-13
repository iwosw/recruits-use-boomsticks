package org.iwoss.recruits_use_boomsticks.ai;

import org.junit.jupiter.api.Test;

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
}
