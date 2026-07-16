package org.iwoss.recruits_use_boomsticks.compat;

import java.util.Objects;

/**
 * Immutable protocol catalog entry for one Artillery Addon weapon.
 *
 * <p>The reload and cooldown values are the NPC policy owned by this project. The addon exposes
 * player-use timing, not a server-safe NPC timing API, so these values must not be confused with
 * a call into the addon's player procedures.</p>
 */
public record ArtilleryWeaponProfile(
        String registryId,
        String ammoId,
        String projectileEntityId,
        int nativeAmmoCode,
        boolean usesNativeLoadedFlag,
        int loadedStage,
        int firedStage,
        int reloadTicks,
        int cooldownTicks,
        int projectileCount,
        double projectileVelocity,
        float inaccuracy,
        double baseDamage,
        boolean critical,
        BoomstickSound firingSound,
        boolean usesNativeAmmoCode
) {
    public ArtilleryWeaponProfile {
        requireId(registryId, "registryId");
        requireId(ammoId, "ammoId");
        requireId(projectileEntityId, "projectileEntityId");
        Objects.requireNonNull(firingSound, "firingSound");
        if (nativeAmmoCode < 0) {
            throw new IllegalArgumentException("nativeAmmoCode must be non-negative");
        }
        if (loadedStage < 0 || firedStage < 0) {
            throw new IllegalArgumentException("native stages must be non-negative");
        }
        if (reloadTicks <= 0) {
            throw new IllegalArgumentException("reloadTicks must be positive");
        }
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must be non-negative");
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
        if (!Double.isFinite(baseDamage) || baseDamage < 0.0D) {
            throw new IllegalArgumentException("baseDamage must be finite and non-negative");
        }
    }

    public BoomstickWeaponProfile toBoomstickProfile() {
        BoomstickAmmoType ammoType = SupportedArtillery.VANILLA_ARROW_ID.equals(ammoId)
                ? BoomstickAmmoType.ARROW
                : BoomstickAmmoType.ROUND_BALL;
        return new BoomstickWeaponProfile(
                registryId,
                ammoType,
                projectileCount,
                projectileVelocity,
                inaccuracy,
                firingSound
        );
    }

    private static void requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
