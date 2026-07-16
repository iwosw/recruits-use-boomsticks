package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Explicit Artillery Addon registry catalog; broad addon tags are deliberately not used. */
public final class SupportedArtillery {
    public static final String MOD_ID = "artillery_addon";

    public static final String HANDGONNE_ID = MOD_ID + ":handgonne";
    public static final String ARQUEBUS_ID = MOD_ID + ":arquebus";
    public static final String MATCHLOCK_MUSKET_ID = MOD_ID + ":matchlock_musket";
    public static final String MATCHLOCK_PISTOL_ID = MOD_ID + ":matchlock_pistol";
    public static final String TORADAR_RIFLE_ID = MOD_ID + ":toradar_rifle";
    public static final String MARKMENGONNE_ID = MOD_ID + ":markmengonne";

    public static final String IRON_BALL_ID = MOD_ID + ":iron_ball";
    public static final String VANILLA_ARROW_ID = "minecraft:arrow";
    public static final String IRONBALL_PROJECTILE_ID = MOD_ID + ":ironball_projectile";
    public static final String IRON_BIT_PROJECTILE_ID = MOD_ID + ":iron_bit_projectile";

    private static final int LOADED_STAGE = 2;
    private static final int FIRED_STAGE = 3;
    private static final int NPC_RELOAD_TICKS = 40;
    private static final int NPC_COOLDOWN_TICKS = 20;

    private static final Map<String, ArtilleryWeaponProfile> PROFILES = createProfiles();

    private SupportedArtillery() {
    }

    public static Set<String> supportedWeaponIds() {
        return PROFILES.keySet();
    }

    public static Map<String, ArtilleryWeaponProfile> profiles() {
        return PROFILES;
    }

    public static Optional<ArtilleryWeaponProfile> profileFor(String registryId) {
        return Optional.ofNullable(PROFILES.get(registryId));
    }

    public static Optional<ArtilleryWeaponProfile> profileFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return profileFor(registryId(stack));
    }

    public static boolean isSupportedAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = registryId(stack);
        return IRON_BALL_ID.equals(id) || VANILLA_ARROW_ID.equals(id);
    }

    /** The first executable gameplay slice intentionally contains only the Arquebus. */
    public static boolean isFirstSliceWeapon(String registryId) {
        return ARQUEBUS_ID.equals(registryId);
    }

    private static String registryId(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static Map<String, ArtilleryWeaponProfile> createProfiles() {
        Map<String, ArtilleryWeaponProfile> profiles = new LinkedHashMap<>();
        profiles.put(HANDGONNE_ID, ironBallProfile(
                HANDGONNE_ID, true, 4.5D, 4.9F, 2.7D));
        profiles.put(ARQUEBUS_ID, ironBallProfile(
                ARQUEBUS_ID, false, 6.0D, 5.5F, 3.75D));
        profiles.put(MATCHLOCK_MUSKET_ID, ironBallProfile(
                MATCHLOCK_MUSKET_ID, false, 7.0D, 5.0F, 4.5D));
        profiles.put(MATCHLOCK_PISTOL_ID, ironBallProfile(
                MATCHLOCK_PISTOL_ID, false, 6.0D, 6.0F, 2.85D));
        profiles.put(TORADAR_RIFLE_ID, ironBallProfile(
                TORADAR_RIFLE_ID, false, 7.0D, 6.0F, 4.6D));
        profiles.put(MARKMENGONNE_ID, new ArtilleryWeaponProfile(
                MARKMENGONNE_ID,
                VANILLA_ARROW_ID,
                VANILLA_ARROW_ID,
                2,
                false,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                4.5D,
                3.3F,
                2.9D,
                true,
                BoomstickSound.ARTILLERY_FIRE,
                false));
        return Collections.unmodifiableMap(profiles);
    }

    private static ArtilleryWeaponProfile ironBallProfile(
            String registryId,
            boolean usesNativeLoadedFlag,
            double velocity,
            float inaccuracy,
            double baseDamage
    ) {
        return new ArtilleryWeaponProfile(
                registryId,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                usesNativeLoadedFlag,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                velocity,
                inaccuracy,
                baseDamage,
                false,
                BoomstickSound.ARTILLERY_FIRE,
                false);
    }
}
