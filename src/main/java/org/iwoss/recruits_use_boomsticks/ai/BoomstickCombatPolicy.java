package org.iwoss.recruits_use_boomsticks.ai;

/** Pure policy shared by the combat goal and Mixins. */
public final class BoomstickCombatPolicy {
    private BoomstickCombatPolicy() {
    }

    public static boolean shouldSuppressOriginalGoal(boolean compatibilityEnabled, boolean supportedWeaponAvailable) {
        return compatibilityEnabled && supportedWeaponAvailable;
    }

    public static boolean shouldUseStrategicFire(boolean validCombatTarget, boolean strategicPositionAvailable) {
        return !validCombatTarget && strategicPositionAvailable;
    }

    public static boolean shouldApproachTarget(boolean hasLineOfSight, double distanceSquared, double combatRange) {
        if (!Double.isFinite(distanceSquared) || !Double.isFinite(combatRange) || combatRange <= 0.0D) {
            return false;
        }
        return !hasLineOfSight || distanceSquared > combatRange * combatRange;
    }

    public static boolean isWithinCombatRange(double distanceSquared, double combatRange) {
        return Double.isFinite(distanceSquared)
                && Double.isFinite(combatRange)
                && combatRange > 0.0D
                && distanceSquared <= combatRange * combatRange;
    }

    public static int reloadTicks(int baseReloadTicks, boolean mounted, boolean arbalest) {
        long normalizedTicks = arbalest
                ? Math.min(25L, Math.max(1L, baseReloadTicks))
                : Math.max(1L, baseReloadTicks);
        long adjustedTicks = mounted ? normalizedTicks * 2L : normalizedTicks;
        return (int) Math.min(Integer.MAX_VALUE, adjustedTicks);
    }

    public static int cooldownTicks() {
        return 5;
    }

    public static boolean isMisfire(boolean firearm, float randomRoll, int probabilityPercent) {
        return firearm
                && Float.isFinite(randomRoll)
                && randomRoll >= 0.0F
                && randomRoll < probabilityPercent / 100.0F;
    }
}
