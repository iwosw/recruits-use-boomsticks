package org.iwoss.recruits_use_boomsticks.compat;

/** Pure lifetime policy for recruit-owned projectiles claimed by a registered adapter. */
public final class BoomstickProjectilePolicy {
    private BoomstickProjectilePolicy() {
    }

    public static boolean shouldApply(boolean compatibilityEnabled, boolean supportedProjectile, boolean recruitOwned) {
        return compatibilityEnabled && supportedProjectile && recruitOwned;
    }

    public static boolean shouldDiscard(boolean pickupAllowed, boolean inGround, int ageTicks, int maxAgeTicks) {
        return (!pickupAllowed || !inGround) && ageTicks >= maxAgeTicks;
    }
}
