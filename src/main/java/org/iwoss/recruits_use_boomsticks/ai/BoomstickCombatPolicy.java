package org.iwoss.recruits_use_boomsticks.ai;

/** Pure policy shared by the combat goal and Mixins. */
public final class BoomstickCombatPolicy {
    private BoomstickCombatPolicy() {
    }

    public static boolean shouldSuppressOriginalGoal(boolean compatibilityEnabled, boolean supportedWeaponAvailable) {
        return compatibilityEnabled && supportedWeaponAvailable;
    }

    public static boolean shouldSuppressOriginalGoal(
            boolean compatibilityEnabled,
            boolean supportedHeldWeapon,
            boolean supportedInventoryWeapon
    ) {
        return shouldSuppressOriginalGoal(
                compatibilityEnabled,
                supportedHeldWeapon || supportedInventoryWeapon);
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

    /**
     * Narrows the shared combat range to what a weapon can actually reach.
     *
     * <p>A weapon may only shorten the range, never extend it, and a nonsensical report falls back
     * to the shared maximum rather than pinning a recruit in place.</p>
     */
    public static double clampCombatRange(double weaponRange, double maxRange) {
        if (!Double.isFinite(maxRange) || maxRange <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(weaponRange) || weaponRange <= 0.0D) {
            return maxRange;
        }
        return Math.min(weaponRange, maxRange);
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
