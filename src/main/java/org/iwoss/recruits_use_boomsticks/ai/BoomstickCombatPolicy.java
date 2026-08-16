package org.iwoss.recruits_use_boomsticks.ai;

/** Pure policy shared by the combat goal and Mixins. */
public final class BoomstickCombatPolicy {
    /**
     * Recruits' passive aggro state, as the inventory and command screens write it.
     *
     * <p>The other three values Recruits stores in the same field are neutral (0), aggressive (1)
     * and raid (2). Upstream's own ranged goal refuses every enemy on this one value, so a passive
     * recruit does not shoot back even at a mob already hitting it.</p>
     */
    public static final int PASSIVE_AGGRO_STATE = 3;

    private BoomstickCombatPolicy() {
    }

    /**
     * Whether the recruit's standing order lets it fight an enemy at all.
     *
     * <p>Which enemies a neutral, aggressive or raiding recruit accepts is Recruits' decision and is
     * made where its target goals run; this goal only has to honour the one order that removes every
     * enemy, because it is the one order the goal could otherwise overrule by handing a target back
     * to the entity itself.</p>
     */
    public static boolean allowsEntityCombat(int aggroState) {
        return aggroState != PASSIVE_AGGRO_STATE;
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

    /**
     * Whether a recruit may ignore the formation overkill limit and shoot back.
     *
     * <p>The fire coordinator normally hands out only as many aim slots as a target can survive, so
     * a third recruit stands idle while two comrades finish the kill. That is correct for a target
     * the formation picked, and wrong for a target that picked a recruit: an enemy already swinging
     * at a recruit must be answered by that recruit regardless of who else has reserved a shot.</p>
     */
    public static boolean allowsSelfDefenseFire(boolean targetAggroedOnShooter, boolean targetHurtShooter) {
        return targetAggroedOnShooter || targetHurtShooter;
    }

    /**
     * Paces the search for a target the formation has not already covered.
     *
     * <p>The search itself is an area entity lookup, the one genuinely expensive part of retargeting,
     * so it runs on an interval rather than every tick. The per-entity stagger spreads a company's
     * searches across several ticks instead of paying for the whole firing line in one.</p>
     */
    public static long nextTargetScanTick(long gameTime, int intervalTicks, int staggerTicks, int entityId) {
        long interval = Math.max(1, intervalTicks);
        long stagger = staggerTicks <= 0 ? 0L : Math.floorMod((long) entityId, (long) staggerTicks);
        long delay = interval + stagger;
        return gameTime > Long.MAX_VALUE - delay ? Long.MAX_VALUE : gameTime + delay;
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
