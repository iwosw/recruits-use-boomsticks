package org.iwoss.recruits_use_boomsticks.compat;

import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

/** Damage tuning that applies only at the recruit-owned compatibility projectile boundary. */
public final class BoomstickDamagePolicy {
    private BoomstickDamagePolicy() {
    }

    public static float configuredDamage(float nativeDamage, RecruitWeaponIntegration integration) {
        return (float) calculateDamage(
                nativeDamage,
                CompatConfig.MINIMUM_PROJECTILE_DAMAGE.get(),
                CompatConfig.PROJECTILE_DAMAGE_MULTIPLIER.get(),
                CompatConfig.integrationDamageMultiplier(integration));
    }

    /** Pure calculation kept separate from Forge config state for focused contract tests. */
    public static double calculateDamage(
            double nativeDamage,
            double minimumDamage,
            double globalMultiplier,
            double integrationMultiplier
    ) {
        if (!Double.isFinite(nativeDamage) || nativeDamage <= 0.0D) {
            return 0.0D;
        }
        double scaled = nativeDamage * nonNegative(globalMultiplier) * nonNegative(integrationMultiplier);
        double floored = Math.max(nonNegative(minimumDamage), scaled);
        return Double.isFinite(floored) ? Math.min(floored, Float.MAX_VALUE) : Float.MAX_VALUE;
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }
}
