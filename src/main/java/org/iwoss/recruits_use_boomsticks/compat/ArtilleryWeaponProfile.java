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
        float alternateInaccuracy,
        double baseDamage,
        boolean critical,
        int pierceLevel,
        boolean silent,
        BoomstickSound firingSound,
        boolean usesNativeAmmoCode,
        boolean nativeAmmoCodeIsDouble,
        InaccuracyBranch alternateInaccuracyBranch,
        boolean pickupAllowed,
        boolean usesNativePowderMarker,
        NativeStateMode nativeStateMode,
        NativeMisfirePolicy nativeMisfirePolicy
    ) {
    public enum InaccuracyBranch {
        NONE,
        PASSENGER,
        SHIFT,
        FORK_REST
    }

    public enum NativeStateMode {
        STAGED,
        AMMO_COUNT,
        DOUBLE_BARREL_FIRST
    }

    public record NativeMisfirePolicy(
            boolean enabled,
            double randomMinimum,
            double randomMaximum,
            double damageThresholdOffset
    ) {
        public static final NativeMisfirePolicy NONE = new NativeMisfirePolicy(false, 0.0D, 0.0D, 0.0D);

        public NativeMisfirePolicy {
            if (enabled && (!Double.isFinite(randomMinimum)
                    || !Double.isFinite(randomMaximum)
                    || randomMinimum >= randomMaximum
                    || !Double.isFinite(damageThresholdOffset)
                    || damageThresholdOffset < 0.0D)) {
                throw new IllegalArgumentException("invalid native misfire policy");
            }
        }

        public boolean misfires(double randomValue, int itemDamage) {
            return enabled && randomValue < itemDamage + damageThresholdOffset;
        }
    }

    public ArtilleryWeaponProfile {
        requireId(registryId, "registryId");
        requireId(ammoId, "ammoId");
        requireId(projectileEntityId, "projectileEntityId");
        Objects.requireNonNull(firingSound, "firingSound");
        Objects.requireNonNull(alternateInaccuracyBranch, "alternateInaccuracyBranch");
        Objects.requireNonNull(nativeStateMode, "nativeStateMode");
        Objects.requireNonNull(nativeMisfirePolicy, "nativeMisfirePolicy");
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
        if (!Float.isFinite(alternateInaccuracy) || alternateInaccuracy < 0.0F) {
            throw new IllegalArgumentException("alternateInaccuracy must be finite and non-negative");
        }
        if (!Double.isFinite(baseDamage) || baseDamage < 0.0D) {
            throw new IllegalArgumentException("baseDamage must be finite and non-negative");
        }
        if (pierceLevel < 0 || pierceLevel > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("pierceLevel must fit in an unsigned byte");
        }
        if (nativeAmmoCodeIsDouble && !usesNativeAmmoCode) {
            throw new IllegalArgumentException("a native ammo marker type requires usesNativeAmmoCode");
        }
        if (nativeStateMode == NativeStateMode.AMMO_COUNT
                && (!usesNativeAmmoCode || !nativeAmmoCodeIsDouble || nativeAmmoCode == 0)) {
            throw new IllegalArgumentException("an ammo-count state requires a positive native double ammo marker");
        }
        if (nativeStateMode == NativeStateMode.DOUBLE_BARREL_FIRST
                && (!usesNativeAmmoCode
                || !nativeAmmoCodeIsDouble
                || nativeAmmoCode == 0
                || usesNativeLoadedFlag
                || usesNativePowderMarker)) {
            throw new IllegalArgumentException(
                    "a double-barrel state requires a positive native double barrel marker without loaded/powder flags");
        }
    }

    public boolean usesNativeStageMarker() {
        return nativeStateMode == NativeStateMode.STAGED;
    }

    /**
     * Native rounds one NPC reload transaction commits.
     *
     * <p>An ammo-count weapon stores its remaining rounds in the native {@code ammo} double, so its
     * confirmed loaded code doubles as the magazine capacity. Every other protocol is single-shot.</p>
     */
    public int magazineSize() {
        return nativeStateMode == NativeStateMode.AMMO_COUNT ? nativeAmmoCode : 1;
    }

    /** Physical ammunition units one complete NPC reload transaction consumes. */
    public int ammoPerReload() {
        return projectileCount * magazineSize();
    }

    public ArtilleryWeaponProfile(
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
            float alternateInaccuracy,
            double baseDamage,
            boolean critical,
            BoomstickSound firingSound,
            boolean usesNativeAmmoCode,
            InaccuracyBranch alternateInaccuracyBranch
    ) {
        this(
                registryId,
                ammoId,
                projectileEntityId,
                nativeAmmoCode,
                usesNativeLoadedFlag,
                loadedStage,
                firedStage,
                reloadTicks,
                cooldownTicks,
                projectileCount,
                projectileVelocity,
                inaccuracy,
                alternateInaccuracy,
                baseDamage,
                critical,
                0,
                true,
                firingSound,
                usesNativeAmmoCode,
                false,
                alternateInaccuracyBranch,
                false,
                true,
                NativeStateMode.STAGED,
                NativeMisfirePolicy.NONE
        );
    }

    public ArtilleryWeaponProfile(
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
            float alternateInaccuracy,
            double baseDamage,
            boolean critical,
            BoomstickSound firingSound,
            boolean usesNativeAmmoCode,
            boolean nativeAmmoCodeIsDouble,
            InaccuracyBranch alternateInaccuracyBranch
    ) {
        this(
                registryId,
                ammoId,
                projectileEntityId,
                nativeAmmoCode,
                usesNativeLoadedFlag,
                loadedStage,
                firedStage,
                reloadTicks,
                cooldownTicks,
                projectileCount,
                projectileVelocity,
                inaccuracy,
                alternateInaccuracy,
                baseDamage,
                critical,
                0,
                true,
                firingSound,
                usesNativeAmmoCode,
                nativeAmmoCodeIsDouble,
                alternateInaccuracyBranch,
                false,
                true,
                NativeStateMode.STAGED,
                NativeMisfirePolicy.NONE
        );
    }

    public ArtilleryWeaponProfile(
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
            float alternateInaccuracy,
            double baseDamage,
            boolean critical,
            int pierceLevel,
            boolean silent,
            BoomstickSound firingSound,
            boolean usesNativeAmmoCode,
            boolean nativeAmmoCodeIsDouble,
            InaccuracyBranch alternateInaccuracyBranch
    ) {
        this(
                registryId,
                ammoId,
                projectileEntityId,
                nativeAmmoCode,
                usesNativeLoadedFlag,
                loadedStage,
                firedStage,
                reloadTicks,
                cooldownTicks,
                projectileCount,
                projectileVelocity,
                inaccuracy,
                alternateInaccuracy,
                baseDamage,
                critical,
                pierceLevel,
                silent,
                firingSound,
                usesNativeAmmoCode,
                nativeAmmoCodeIsDouble,
                alternateInaccuracyBranch,
                false,
                true,
                NativeStateMode.STAGED,
                NativeMisfirePolicy.NONE
        );
    }

    public ArtilleryWeaponProfile(
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
            float alternateInaccuracy,
            double baseDamage,
            boolean critical,
            BoomstickSound firingSound,
            boolean usesNativeAmmoCode,
            boolean nativeAmmoCodeIsDouble,
            InaccuracyBranch alternateInaccuracyBranch,
            boolean pickupAllowed,
            boolean usesNativePowderMarker
    ) {
        this(
                registryId,
                ammoId,
                projectileEntityId,
                nativeAmmoCode,
                usesNativeLoadedFlag,
                loadedStage,
                firedStage,
                reloadTicks,
                cooldownTicks,
                projectileCount,
                projectileVelocity,
                inaccuracy,
                alternateInaccuracy,
                baseDamage,
                critical,
                0,
                true,
                firingSound,
                usesNativeAmmoCode,
                nativeAmmoCodeIsDouble,
                alternateInaccuracyBranch,
                pickupAllowed,
                usesNativePowderMarker,
                NativeStateMode.STAGED,
                NativeMisfirePolicy.NONE
        );
    }

    public ArtilleryWeaponProfile(
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
            float alternateInaccuracy,
            double baseDamage,
            boolean critical,
            BoomstickSound firingSound,
            boolean usesNativeAmmoCode,
            boolean nativeAmmoCodeIsDouble,
            InaccuracyBranch alternateInaccuracyBranch,
            boolean pickupAllowed,
            boolean usesNativePowderMarker,
            NativeMisfirePolicy nativeMisfirePolicy
    ) {
        this(
                registryId,
                ammoId,
                projectileEntityId,
                nativeAmmoCode,
                usesNativeLoadedFlag,
                loadedStage,
                firedStage,
                reloadTicks,
                cooldownTicks,
                projectileCount,
                projectileVelocity,
                inaccuracy,
                alternateInaccuracy,
                baseDamage,
                critical,
                0,
                true,
                firingSound,
                usesNativeAmmoCode,
                nativeAmmoCodeIsDouble,
                alternateInaccuracyBranch,
                pickupAllowed,
                usesNativePowderMarker,
                NativeStateMode.STAGED,
                nativeMisfirePolicy
        );
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
