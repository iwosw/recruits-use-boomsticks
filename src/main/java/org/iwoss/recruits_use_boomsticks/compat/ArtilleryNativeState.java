package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** NPC-owned representation of the bytecode-confirmed Artillery staged loading protocol. */
public final class ArtilleryNativeState {
    public static final String POWDER_KEY = "powder";
    public static final String STAGE_KEY = "stage";
    public static final String AMMO_KEY = "ammo";
    public static final String LOADED_KEY = "loaded";

    private static final String RELOADING_KEY = "recruits_use_boomsticks:artillery_reloading";
    private static final String FIRING_KEY = "recruits_use_boomsticks:artillery_firing";

    private ArtilleryNativeState() {
    }

    public static boolean isLoaded(ItemStack weapon, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (weapon == null || weapon.isEmpty() || !weapon.hasTag()) {
            return false;
        }
        return isLoadedTag(weapon.getTag(), profile);
    }

    static boolean isLoadedTag(CompoundTag tag, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (tag == null
                || tag.getTagType(POWDER_KEY) != Tag.TAG_DOUBLE
                || tag.getTagType(STAGE_KEY) != Tag.TAG_DOUBLE
                || tag.getDouble(POWDER_KEY) < 1.0D
                || tag.getDouble(STAGE_KEY) != profile.loadedStage()
                || !hasExpectedAmmoMarker(tag, profile)) {
            return false;
        }
        return !profile.usesNativeLoadedFlag() || tag.getBoolean(LOADED_KEY);
    }

    public static void markLoaded(ItemStack weapon, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(profile, "profile");
        markLoadedTag(weapon.getOrCreateTag(), profile);
    }

    static void markLoadedTag(CompoundTag tag, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(profile, "profile");
        tag.putDouble(POWDER_KEY, 1.0D);
        tag.putDouble(STAGE_KEY, profile.loadedStage());
        if (profile.usesNativeAmmoCode()) {
            tag.putInt(AMMO_KEY, profile.nativeAmmoCode());
        } else {
            tag.remove(AMMO_KEY);
        }
        if (profile.usesNativeLoadedFlag()) {
            tag.putBoolean(LOADED_KEY, true);
        } else {
            tag.remove(LOADED_KEY);
        }
    }

    public static void markFired(ItemStack weapon, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(profile, "profile");
        markFiredTag(weapon.getOrCreateTag(), profile);
    }

    static void markFiredTag(CompoundTag tag, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(profile, "profile");
        tag.putDouble(POWDER_KEY, 0.0D);
        tag.putDouble(STAGE_KEY, profile.firedStage());
        if (profile.usesNativeAmmoCode()) {
            tag.putInt(AMMO_KEY, profile.nativeAmmoCode());
        } else {
            tag.remove(AMMO_KEY);
        }
        if (profile.usesNativeLoadedFlag()) {
            tag.putBoolean(LOADED_KEY, false);
        } else {
            tag.remove(LOADED_KEY);
        }
    }

    private static boolean hasExpectedAmmoMarker(CompoundTag tag, ArtilleryWeaponProfile profile) {
        if (!profile.usesNativeAmmoCode()) {
            // Arquebus does not write this field in its native player procedure, but an
            // optional compatibility marker is valid when it uses the profile's confirmed
            // integer code. A conflicting type or value must still invalidate the payload.
            return !tag.contains(AMMO_KEY)
                    || (tag.getTagType(AMMO_KEY) == Tag.TAG_INT
                    && tag.getInt(AMMO_KEY) == profile.nativeAmmoCode());
        }
        return tag.contains(AMMO_KEY, Tag.TAG_INT)
                && tag.getInt(AMMO_KEY) == profile.nativeAmmoCode();
    }

    public static void setReloading(ItemStack weapon, boolean reloading) {
        if (weapon == null || weapon.isEmpty()) {
            return;
        }
        CompoundTag tag = weapon.getOrCreateTag();
        if (reloading) {
            tag.putBoolean(RELOADING_KEY, true);
        } else {
            tag.remove(RELOADING_KEY);
        }
    }

    public static boolean isReloading(ItemStack weapon) {
        return weapon != null && !weapon.isEmpty() && weapon.hasTag()
                && weapon.getTag() != null
                && weapon.getTag().getBoolean(RELOADING_KEY);
    }

    public static void setFiring(ItemStack weapon, boolean firing) {
        if (weapon == null || weapon.isEmpty()) {
            return;
        }
        CompoundTag tag = weapon.getOrCreateTag();
        if (firing) {
            tag.putBoolean(FIRING_KEY, true);
        } else {
            tag.remove(FIRING_KEY);
        }
    }
}
