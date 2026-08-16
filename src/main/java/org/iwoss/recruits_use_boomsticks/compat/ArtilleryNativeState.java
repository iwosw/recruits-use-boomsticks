package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** NPC-owned representation of the bytecode-confirmed Artillery staged loading protocol. */
public final class ArtilleryNativeState {
    public static final String POWDER_KEY = "powder";
    public static final String STAGE_KEY = "stage";
    public static final String AMMO_KEY = "ammo";
    public static final String LOADED_KEY = "loaded";
    public static final String BARREL_ONE_KEY = "barrel_one";
    public static final String BARREL_TWO_KEY = "barrel_two";
    public static final String RAMMED_ONE_KEY = "rammed_one";
    public static final String RAMMED_TWO_KEY = "rammed_two";
    private static final String DISPLAY_KEY = "display";
    private static final String LORE_KEY = "Lore";

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
        if (profile.nativeStateMode() == ArtilleryWeaponProfile.NativeStateMode.DOUBLE_BARREL_FIRST) {
            return isDoubleBarrelFirstLoaded(tag, profile);
        }
        if (tag == null
                || !hasExpectedStageMarker(tag, profile)
                || !hasExpectedPowderMarker(tag, profile)
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
        if (profile.nativeStateMode() == ArtilleryWeaponProfile.NativeStateMode.DOUBLE_BARREL_FIRST) {
            markDoubleBarrelFirstLoaded(tag, profile);
            return;
        }
        if (profile.usesNativePowderMarker()) {
            tag.putDouble(POWDER_KEY, 1.0D);
        } else {
            tag.remove(POWDER_KEY);
        }
        if (profile.usesNativeStageMarker()) {
            tag.putDouble(STAGE_KEY, profile.loadedStage());
        } else {
            tag.remove(STAGE_KEY);
        }
        if (profile.usesNativeAmmoCode()) {
            putNativeAmmoCode(tag, profile, true);
        } else {
            tag.remove(AMMO_KEY);
        }
        if (profile.usesNativeLoadedFlag()) {
            tag.putBoolean(LOADED_KEY, true);
        } else {
            tag.remove(LOADED_KEY);
        }
    }

    /**
     * Commits one native loading step onto the weapon.
     *
     * <p>Only the values the native procedure writes at that step are touched, so a partially loaded
     * weapon reports itself unloaded until the chain reaches the profile's confirmed loaded stage.</p>
     */
    public static void applyReloadStep(
            ItemStack weapon,
            ArtilleryWeaponProfile profile,
            ArtilleryReloadStep step
    ) {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(step, "step");
        applyReloadStepTag(weapon.getOrCreateTag(), step);
        writeNativeLore(weapon, step.nativeLore());
    }

    /** Commits only the native NBT writes of one step; the display lore is handled by the caller. */
    static void applyReloadStepTag(CompoundTag tag, ArtilleryReloadStep step) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(step, "step");
        for (ArtilleryReloadStep.NativeWrite write : step.writes()) {
            if (write.kind() == ArtilleryReloadStep.NativeWrite.Kind.BOOLEAN) {
                tag.putBoolean(write.key(), write.flag());
            } else {
                tag.putDouble(write.key(), write.number());
            }
        }
    }

    /** Returns the native loading steps already committed on a partially loaded weapon. */
    public static int completedReloadSteps(ItemStack weapon, List<ArtilleryReloadStep> steps) {
        Objects.requireNonNull(steps, "steps");
        if (weapon == null || weapon.isEmpty() || !weapon.hasTag()) {
            return 0;
        }
        CompoundTag tag = weapon.getTag();
        return completedReloadStepsTag(tag, steps);
    }

    static int completedReloadStepsTag(CompoundTag tag, List<ArtilleryReloadStep> steps) {
        Objects.requireNonNull(steps, "steps");
        if (tag == null) {
            return 0;
        }
        // Later steps overwrite stage/ammo values from earlier steps. A boundary is valid only when
        // every last-write-wins value up to it still matches; checking one step alone confuses the
        // fired Carbine's stage 3/powder 0 state with its loaded stage 3/powder 1 state.
        for (int index = steps.size() - 1; index >= 0; index--) {
            if (hasCumulativeWrites(tag, steps, index)) {
                return index + 1;
            }
        }
        return 0;
    }

    private static boolean hasCumulativeWrites(
            CompoundTag tag,
            List<ArtilleryReloadStep> steps,
            int completedIndex
    ) {
        HashSet<String> checkedKeys = new HashSet<>();
        for (int stepIndex = completedIndex; stepIndex >= 0; stepIndex--) {
            for (ArtilleryReloadStep.NativeWrite write : steps.get(stepIndex).writes()) {
                if (!checkedKeys.add(write.key())) {
                    continue;
                }
                if (write.kind() == ArtilleryReloadStep.NativeWrite.Kind.BOOLEAN) {
                    if (tag.getTagType(write.key()) != Tag.TAG_BYTE
                            || tag.getBoolean(write.key()) != write.flag()) {
                        return false;
                    }
                } else if (tag.getTagType(write.key()) != Tag.TAG_DOUBLE
                        || tag.getDouble(write.key()) != write.number()) {
                    return false;
                }
            }
        }
        return !checkedKeys.isEmpty();
    }

    /** Reproduces the native transient display lore without touching any other display data. */
    static void writeNativeLore(ItemStack weapon, String lore) {
        if (lore == null || lore.isBlank()) {
            return;
        }
        CompoundTag display = weapon.getOrCreateTagElement(DISPLAY_KEY);
        ListTag lines = new ListTag();
        lines.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(lore))));
        display.put(LORE_KEY, lines);
    }

    /** Clears the transient loading lore once the weapon leaves the loading transaction. */
    public static void clearNativeLore(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty() || !weapon.hasTag()) {
            return;
        }
        CompoundTag tag = weapon.getTag();
        if (tag == null || !tag.contains(DISPLAY_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag display = tag.getCompound(DISPLAY_KEY);
        display.remove(LORE_KEY);
        if (display.isEmpty()) {
            tag.remove(DISPLAY_KEY);
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
        if (profile.nativeStateMode() == ArtilleryWeaponProfile.NativeStateMode.DOUBLE_BARREL_FIRST) {
            markDoubleBarrelFirstFired(tag);
            return;
        }
        if (profile.usesNativePowderMarker()) {
            tag.putDouble(POWDER_KEY, 0.0D);
        } else {
            tag.remove(POWDER_KEY);
        }
        if (profile.usesNativeStageMarker()) {
            tag.putDouble(STAGE_KEY, profile.firedStage());
        } else {
            tag.remove(STAGE_KEY);
        }
        if (profile.usesNativeAmmoCode()) {
            putNativeAmmoCode(tag, profile, false);
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
        if (profile.nativeStateMode() == ArtilleryWeaponProfile.NativeStateMode.AMMO_COUNT) {
            // Any confirmed native counter state between one round and the magazine capacity is
            // loaded; the repeater fires down through them instead of reloading after every shot.
            return committedRounds(tag, profile) >= 1.0D;
        }
        if (profile.nativeAmmoCodeIsDouble()) {
            return tag.contains(AMMO_KEY, Tag.TAG_DOUBLE)
                    && tag.getDouble(AMMO_KEY) == profile.nativeAmmoCode();
        }
        return tag.contains(AMMO_KEY, Tag.TAG_INT)
                && tag.getInt(AMMO_KEY) == profile.nativeAmmoCode();
    }

    /**
     * Native rounds still committed on the weapon.
     *
     * <p>Ammo-count weapons report their remaining counter; every other protocol is single-shot and
     * reports one when loaded.</p>
     */
    public static int remainingRounds(ItemStack weapon, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (weapon == null || weapon.isEmpty() || !weapon.hasTag()) {
            return 0;
        }
        return remainingRoundsTag(weapon.getTag(), profile);
    }

    static int remainingRoundsTag(CompoundTag tag, ArtilleryWeaponProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (tag == null) {
            return 0;
        }
        if (profile.nativeStateMode() != ArtilleryWeaponProfile.NativeStateMode.AMMO_COUNT) {
            return isLoadedTag(tag, profile) ? 1 : 0;
        }
        return (int) committedRounds(tag, profile);
    }

    /** Reads the native counter, rejecting absent, non-native, fractional, or over-capacity values. */
    private static double committedRounds(CompoundTag tag, ArtilleryWeaponProfile profile) {
        if (tag.getTagType(AMMO_KEY) != Tag.TAG_DOUBLE) {
            return 0.0D;
        }
        double rounds = tag.getDouble(AMMO_KEY);
        if (!Double.isFinite(rounds)
                || rounds < 1.0D
                || rounds > profile.magazineSize()
                || rounds != Math.rint(rounds)) {
            return 0.0D;
        }
        return rounds;
    }

    private static boolean isDoubleBarrelFirstLoaded(CompoundTag tag, ArtilleryWeaponProfile profile) {
        return tag != null
                && tag.getTagType(BARREL_ONE_KEY) == Tag.TAG_DOUBLE
                && tag.getDouble(BARREL_ONE_KEY) == profile.nativeAmmoCode()
                && tag.getTagType(RAMMED_ONE_KEY) == Tag.TAG_DOUBLE
                && tag.getDouble(RAMMED_ONE_KEY) == 1.0D
                && tag.getTagType(LOADED_KEY) == Tag.TAG_DOUBLE
                && tag.getDouble(LOADED_KEY) == 1.0D
                && hasOptionalZeroDouble(tag, BARREL_TWO_KEY)
                && hasOptionalZeroDouble(tag, RAMMED_TWO_KEY)
                && !tag.contains(POWDER_KEY)
                && !tag.contains(STAGE_KEY)
                && !tag.contains(AMMO_KEY);
    }

    private static boolean hasOptionalZeroDouble(CompoundTag tag, String key) {
        return !tag.contains(key)
                || (tag.getTagType(key) == Tag.TAG_DOUBLE && tag.getDouble(key) == 0.0D);
    }

    private static void markDoubleBarrelFirstLoaded(CompoundTag tag, ArtilleryWeaponProfile profile) {
        tag.remove(POWDER_KEY);
        tag.remove(STAGE_KEY);
        tag.remove(AMMO_KEY);
        tag.putDouble(BARREL_ONE_KEY, profile.nativeAmmoCode());
        tag.putDouble(BARREL_TWO_KEY, 0.0D);
        tag.putDouble(RAMMED_ONE_KEY, 1.0D);
        tag.putDouble(RAMMED_TWO_KEY, 0.0D);
        tag.putDouble(LOADED_KEY, 1.0D);
    }

    private static void markDoubleBarrelFirstFired(CompoundTag tag) {
        tag.remove(POWDER_KEY);
        tag.remove(STAGE_KEY);
        tag.remove(AMMO_KEY);
        tag.putDouble(BARREL_ONE_KEY, 0.0D);
        tag.putDouble(BARREL_TWO_KEY, 0.0D);
        tag.putDouble(RAMMED_ONE_KEY, 0.0D);
        tag.putDouble(RAMMED_TWO_KEY, 0.0D);
        tag.putDouble(LOADED_KEY, 0.0D);
    }

    private static boolean hasExpectedStageMarker(CompoundTag tag, ArtilleryWeaponProfile profile) {
        if (!profile.usesNativeStageMarker()) {
            return !tag.contains(STAGE_KEY);
        }
        return tag.getTagType(STAGE_KEY) == Tag.TAG_DOUBLE
                && tag.getDouble(STAGE_KEY) == profile.loadedStage();
    }

    private static boolean hasExpectedPowderMarker(CompoundTag tag, ArtilleryWeaponProfile profile) {
        if (!profile.usesNativePowderMarker()) {
            return !tag.contains(POWDER_KEY);
        }
        return tag.getTagType(POWDER_KEY) == Tag.TAG_DOUBLE
                && tag.getDouble(POWDER_KEY) >= 1.0D;
    }

    private static void putNativeAmmoCode(
            CompoundTag tag,
            ArtilleryWeaponProfile profile,
            boolean loaded
    ) {
        double ammoValue;
        if (profile.nativeStateMode() == ArtilleryWeaponProfile.NativeStateMode.AMMO_COUNT && !loaded) {
            // One shot spends one native round; the counter only reaches zero on the last one.
            ammoValue = Math.max(0.0D, committedRounds(tag, profile) - 1.0D);
        } else {
            ammoValue = profile.nativeAmmoCode();
        }
        if (profile.nativeAmmoCodeIsDouble()) {
            tag.putDouble(AMMO_KEY, ammoValue);
        } else {
            tag.putInt(AMMO_KEY, (int) ammoValue);
        }
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
