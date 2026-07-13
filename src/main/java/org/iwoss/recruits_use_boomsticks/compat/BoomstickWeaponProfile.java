package org.iwoss.recruits_use_boomsticks.compat;

import java.util.Objects;

/**
 * Immutable, server-safe description of one supported Medieval Boomsticks weapon.
 *
 * <p>The profile intentionally contains no Minecraft entity or item instances. That keeps
 * weapon selection and state-machine policy testable without booting a game server.</p>
 */
public record BoomstickWeaponProfile(
        String registryId,
        BoomstickAmmoType ammoType,
        int baseReloadTicks,
        int projectileCount,
        double projectileVelocity,
        float inaccuracy,
        int cooldownTicks,
        boolean projectilePickupAllowed,
        BoomstickSound firingSound,
        BoomstickParticle firingParticle
) {
    public BoomstickWeaponProfile {
        if (registryId == null || registryId.isBlank()) {
            throw new IllegalArgumentException("registryId must not be blank");
        }
        Objects.requireNonNull(ammoType, "ammoType");
        Objects.requireNonNull(firingSound, "firingSound");
        Objects.requireNonNull(firingParticle, "firingParticle");
        if (baseReloadTicks <= 0) {
            throw new IllegalArgumentException("baseReloadTicks must be positive");
        }
        if (projectileCount <= 0) {
            throw new IllegalArgumentException("projectileCount must be positive");
        }
        if (!Double.isFinite(projectileVelocity) || projectileVelocity <= 0.0D) {
            throw new IllegalArgumentException("projectileVelocity must be finite and positive");
        }
        if (!Float.isFinite(inaccuracy) || inaccuracy < 0.0F) {
            throw new IllegalArgumentException("inaccuracy must be finite and non-negative");
        }
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must be non-negative");
        }
    }

    /** Number of matching ammo units needed for one complete volley. */
    public int ammoPerVolley() {
        return projectileCount;
    }
}
