package org.iwoss.recruits_use_boomsticks.ai;

/** Pure policy shared by the combat goal and Mixins. */
public final class BoomstickCombatPolicy {
    private BoomstickCombatPolicy() {
    }

    public static boolean shouldSuppressOriginalGoal(boolean compatibilityEnabled, boolean supportedWeaponAvailable) {
        return compatibilityEnabled && supportedWeaponAvailable;
    }

    public static boolean shouldApproachTarget(boolean hasLineOfSight, double distanceSquared, double combatRange) {
        if (!Double.isFinite(distanceSquared) || !Double.isFinite(combatRange) || combatRange <= 0.0D) {
            return false;
        }
        return !hasLineOfSight || distanceSquared > combatRange * combatRange;
    }
}
