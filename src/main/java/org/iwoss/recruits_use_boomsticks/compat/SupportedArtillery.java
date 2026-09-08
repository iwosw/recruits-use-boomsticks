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
    public static final String MATCHLOCK_CARBINE_ID = MOD_ID + ":matchlock_carbine";
    public static final String MATCHLOCK_PISTOL_ID = MOD_ID + ":matchlock_pistol";
    public static final String TORADAR_RIFLE_ID = MOD_ID + ":toradar_rifle";
    public static final String MINI_PISTOLA_ID = MOD_ID + ":mini_pistola";
    public static final String TILLER_GUN_ID = MOD_ID + ":tiller_gun";
    public static final String NOBLE_HANDGONNE_ID = MOD_ID + ":noble_handgonne";
    public static final String MARKMENGONNE_ID = MOD_ID + ":markmengonne";
    public static final String BRONZE_HANDGONNE_ID = MOD_ID + ":bronze_handgonne";
    public static final String HARQUEBUS_ID = MOD_ID + ":harquebus";
    public static final String HACKBUT_ID = MOD_ID + ":hackbut";
    public static final String WINDLASS_CROSSBOW_ID = MOD_ID + ":windlass_crossbow";
    public static final String CHU_KO_NU_ID = MOD_ID + ":chu_ko_nu";
    public static final String TACCOLA_HANDGONNE_ID = MOD_ID + ":taccola_handgonne";
    public static final String HAND_CANNON_ID = MOD_ID + ":hand_cannon";
    public static final String DOUBLE_BARREL_GONNE_ID = MOD_ID + ":double_barrel_gonne";
    public static final String WHEELLOCK_PISTOL_ID = MOD_ID + ":wheellock_pistol";
    public static final String WHEELLOCK_MUSKET_ID = MOD_ID + ":wheellock_musket";
    public static final String WHEELLOCK_HUNTING_RIFLE_ID = MOD_ID + ":wheellock_hunting_rifle";
    public static final String WHEELLOCK_BREECHLOADING_RIFLE_ID = MOD_ID + ":wheellock_breechloading_rifle";
    public static final String DUAL_WHEELLOCK_PISTOL_ID = MOD_ID + ":dual_wheellock_pistol";
    public static final String DUAL_WHEELLOCK_CARBINE_ID = MOD_ID + ":dual_wheellock_carbine";
    public static final String LOADED_CARTRIDGE_ID = MOD_ID + ":loaded_cartridge";
    public static final String EMPTY_CARTRIDGE_ID = MOD_ID + ":empty_cartridge";

    public static final String IRON_BALL_ID = MOD_ID + ":iron_ball";
    public static final String SMALL_IRON_BALL_ID = MOD_ID + ":small_iron_ball";
    public static final String LARGE_IRON_BALL_ID = MOD_ID + ":large_iron_ball";
    public static final String FORK_REST_ID = MOD_ID + ":fork_rest";
    public static final String VANILLA_ARROW_ID = "minecraft:arrow";
    public static final String VANILLA_STICK_ID = "minecraft:stick";
    public static final String IRONBALL_PROJECTILE_ID = MOD_ID + ":ironball_projectile";
    public static final String IRON_BIT_PROJECTILE_ID = MOD_ID + ":iron_bit_projectile";

    private static final int LOADED_STAGE = 2;
    private static final int FIRED_STAGE = 3;
    private static final int NPC_RELOAD_TICKS = 40;
    private static final int NPC_COOLDOWN_TICKS = 20;
    /**
     * Carbine and Harquebus load one stage higher than the rest of the matchlock family.
     *
     * <p>Their native chains run stage 1.0, 2.0, 3.0 and their firing branches check stage 3.0.</p>
     */
    private static final int SHIFTED_LOADED_STAGE = 3;
    private static final int HARQUEBUS_FIRED_STAGE = 0;
    private static final int WINDLASS_LOADED_STAGE = 4;
    private static final int WINDLASS_FIRED_STAGE = 0;
    /** Native Chu Ko Nu magazine capacity; its counter and lore run up to eight rounds. */
    private static final int CHU_KO_NU_MAGAZINE = ArtilleryReloadProtocol.CHU_KO_NU_CAPACITY;

    private static final Map<String, ArtilleryWeaponProfile> PROFILES = createProfiles();
    private static final ArtilleryWeaponProfile NOBLE_HANDGONNE_IRON_BALL_PROFILE =
            createNobleHandgonneIronBallProfile();
    private static final ArtilleryWeaponProfile MARKMENGONNE_IRON_BALL_PROFILE =
            createMarkmengonneIronBallProfile();
    private static final Set<String> GAMEPLAY_WEAPON_IDS = Set.of(
            HANDGONNE_ID,
            ARQUEBUS_ID,
            MATCHLOCK_MUSKET_ID,
            MATCHLOCK_CARBINE_ID,
            MATCHLOCK_PISTOL_ID,
            TORADAR_RIFLE_ID,
            MINI_PISTOLA_ID,
            TILLER_GUN_ID,
            NOBLE_HANDGONNE_ID,
            MARKMENGONNE_ID,
            BRONZE_HANDGONNE_ID,
            HARQUEBUS_ID,
            HACKBUT_ID,
            WINDLASS_CROSSBOW_ID,
            CHU_KO_NU_ID,
            TACCOLA_HANDGONNE_ID,
            HAND_CANNON_ID,
            DOUBLE_BARREL_GONNE_ID,
            WHEELLOCK_PISTOL_ID, WHEELLOCK_MUSKET_ID, WHEELLOCK_HUNTING_RIFLE_ID,
            WHEELLOCK_BREECHLOADING_RIFLE_ID, DUAL_WHEELLOCK_PISTOL_ID, DUAL_WHEELLOCK_CARBINE_ID
    );

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

    /** Native Noble Handgonne iron-ball branch, selected when a recruit owns an iron ball. */
    public static ArtilleryWeaponProfile nobleHandgonneIronBallProfile() {
        return NOBLE_HANDGONNE_IRON_BALL_PROFILE;
    }

    /** Native Markmen's Handgonne iron-ball branch, preferred when a recruit owns an iron ball. */
    public static ArtilleryWeaponProfile markmengonneIronBallProfile() {
        return MARKMENGONNE_IRON_BALL_PROFILE;
    }

    public static boolean isSupportedAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = registryId(stack);
        return IRON_BALL_ID.equals(id)
                || SMALL_IRON_BALL_ID.equals(id)
                || LARGE_IRON_BALL_ID.equals(id)
                || VANILLA_ARROW_ID.equals(id)
                || LOADED_CARTRIDGE_ID.equals(id);
    }

    /** Explicit gameplay allowlist; the remaining profiles are reconnaissance data only. */
    public static Set<String> gameplayWeaponIds() {
        return GAMEPLAY_WEAPON_IDS;
    }

    public static boolean isGameplayWeapon(String registryId) {
        return GAMEPLAY_WEAPON_IDS.contains(registryId);
    }

    private static String registryId(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static Map<String, ArtilleryWeaponProfile> createProfiles() {
        Map<String, ArtilleryWeaponProfile> profiles = new LinkedHashMap<>();
        // The native ball step writes the double `ammo=0.0` marker, so the recruit payload carries it
        // exactly like the Taccola branch does.
        profiles.put(HANDGONNE_ID, new ArtilleryWeaponProfile(
                HANDGONNE_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                true,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                4.5D,
                9.0F,
                6.5F,
                1.85D,
                false,
                0,
                true,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT));
        profiles.put(TACCOLA_HANDGONNE_ID, new ArtilleryWeaponProfile(
                TACCOLA_HANDGONNE_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                true,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                4.5D,
                9.0F,
                6.5F,
                1.85D,
                false,
                0,
                true,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT));
        profiles.put(HAND_CANNON_ID, new ArtilleryWeaponProfile(
                HAND_CANNON_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                false,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                3,
                4.5D,
                10.0F,
                7.0F,
                2.0D,
                false,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                false,
                false,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT,
                false,
                true,
                new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1.0D, 205.0D, 15.0D)));
        profiles.put(DOUBLE_BARREL_GONNE_ID, new ArtilleryWeaponProfile(
                DOUBLE_BARREL_GONNE_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                2,
                false,
                1,
                0,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                4.5D,
                9.0F,
                4.5F,
                2.7D,
                true,
                1,
                false,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT,
                false,
                false,
                ArtilleryWeaponProfile.NativeStateMode.DOUBLE_BARREL_FIRST,
                new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1.0D, 105.0D, 0.0D)));
        profiles.put(ARQUEBUS_ID, ironBallProfile(
                ARQUEBUS_ID, false, 6.0D, 5.5F, 3.75D));
        profiles.put(MATCHLOCK_MUSKET_ID, ironBallProfile(
                MATCHLOCK_MUSKET_ID, false, 7.0D, 5.0F, 4.5D));
        // CarbineRightclickProcedure loads through stages 1.0, 2.0, 3.0 and fires at stage 3.0.
        profiles.put(MATCHLOCK_CARBINE_ID, new ArtilleryWeaponProfile(
                MATCHLOCK_CARBINE_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                false,
                SHIFTED_LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                6.5D,
                6.0F,
                3.5F,
                2.7D,
                false,
                BoomstickSound.ARTILLERY_FIRE,
                false,
                ArtilleryWeaponProfile.InaccuracyBranch.FORK_REST));
        profiles.put(MATCHLOCK_PISTOL_ID, ironBallProfile(
                MATCHLOCK_PISTOL_ID, false, 6.0D, 6.0F, 1.9D, true));
        profiles.put(TORADAR_RIFLE_ID, ironBallProfile(
                TORADAR_RIFLE_ID, false, 7.0D, 6.0F, 3.2D));
        profiles.put(MINI_PISTOLA_ID, new ArtilleryWeaponProfile(
                MINI_PISTOLA_ID,
                SMALL_IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                false,
                1,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                3.3D,
                10.0F,
                11.0F,
                1.0D,
                false,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                false,
                ArtilleryWeaponProfile.InaccuracyBranch.PASSENGER));
        // The native Tiller ramming step writes `loaded=true` and its ball step the double
        // `ammo=0.0` marker.
        profiles.put(TILLER_GUN_ID, new ArtilleryWeaponProfile(
                TILLER_GUN_ID,
                SMALL_IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                true,
                2,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                3.4D,
                8.0F,
                4.0F,
                2.0D,
                false,
                0,
                true,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT));
        // The native Arrow branch reaches stage 2.0 with `ammo=2.0` and never runs the ramming step
        // that would have written a `loaded` flag, so the recruit payload must not invent one.
        profiles.put(NOBLE_HANDGONNE_ID, new ArtilleryWeaponProfile(
                NOBLE_HANDGONNE_ID,
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
                8.5F,
                4.0F,
                2.55D,
                false,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT));
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
                6.5F,
                4.0F,
                1.5D,
                false,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT));
        // BronzegunneRightclickedProcedure is not included in Artillery's own guns tag, but exposes
        // the same confirmed staged iron-ball boundary as the handgonne family.
        profiles.put(BRONZE_HANDGONNE_ID, new ArtilleryWeaponProfile(
                BRONZE_HANDGONNE_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                true,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                4.5D,
                9.0F,
                4.5F,
                1.85D,
                false,
                0,
                true,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT,
                false,
                true,
                ArtilleryWeaponProfile.NativeStateMode.STAGED,
                new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1.0D, 89.0D, 5.0D)));
        profiles.put(HARQUEBUS_ID, new ArtilleryWeaponProfile(
                HARQUEBUS_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                false,
                SHIFTED_LOADED_STAGE,
                HARQUEBUS_FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                5.5D,
                6.0F,
                3.5F,
                2.2D,
                false,
                BoomstickSound.ARTILLERY_FIRE,
                false,
                ArtilleryWeaponProfile.InaccuracyBranch.FORK_REST));
        profiles.put(HACKBUT_ID, new ArtilleryWeaponProfile(
                HACKBUT_ID,
                LARGE_IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                false,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                6.5D,
                5.5F,
                5.5F,
                5.25D,
                true,
                1,
                false,
                BoomstickSound.ARTILLERY_FIRE,
                false,
                false,
                ArtilleryWeaponProfile.InaccuracyBranch.NONE));
        profiles.put(WINDLASS_CROSSBOW_ID, new ArtilleryWeaponProfile(
                WINDLASS_CROSSBOW_ID,
                VANILLA_ARROW_ID,
                VANILLA_ARROW_ID,
                0,
                false,
                WINDLASS_LOADED_STAGE,
                WINDLASS_FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                3.5D,
                1.0F,
                1.0F,
                2.6D,
                true,
                BoomstickSound.CROSSBOW_SHOOT,
                false,
                false,
                ArtilleryWeaponProfile.InaccuracyBranch.NONE,
                true,
                false));
        profiles.put(CHU_KO_NU_ID, new ArtilleryWeaponProfile(
                CHU_KO_NU_ID,
                VANILLA_ARROW_ID,
                VANILLA_ARROW_ID,
                CHU_KO_NU_MAGAZINE,
                false,
                0,
                0,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                2.4D,
                0.5F,
                0.5F,
                1.6D,
                true,
                0,
                false,
                BoomstickSound.CROSSBOW_SHOOT,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.NONE,
                true,
                false,
                ArtilleryWeaponProfile.NativeStateMode.AMMO_COUNT,
                ArtilleryWeaponProfile.NativeMisfirePolicy.NONE));
        Artillery1162Profiles.addWheellocks(profiles);
        return Collections.unmodifiableMap(profiles);
    }

    private static ArtilleryWeaponProfile ironBallProfile(
            String registryId,
            boolean usesNativeLoadedFlag,
            double velocity,
            float inaccuracy,
            double baseDamage
    ) {
        return ironBallProfile(
                registryId,
                usesNativeLoadedFlag,
                velocity,
                inaccuracy,
                baseDamage,
                false
        );
    }

    private static ArtilleryWeaponProfile ironBallProfile(
            String registryId,
            boolean usesNativeLoadedFlag,
            double velocity,
            float inaccuracy,
            double baseDamage,
            boolean critical
    ) {
        return ironBallProfile(
                registryId,
                usesNativeLoadedFlag,
                velocity,
                inaccuracy,
                inaccuracy,
                baseDamage,
                critical,
                ArtilleryWeaponProfile.InaccuracyBranch.PASSENGER
        );
    }

    private static ArtilleryWeaponProfile ironBallProfile(
            String registryId,
            boolean usesNativeLoadedFlag,
            double velocity,
            float inaccuracy,
            float alternateInaccuracy,
            double baseDamage,
            boolean critical,
            ArtilleryWeaponProfile.InaccuracyBranch alternateInaccuracyBranch
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
                alternateInaccuracy,
                baseDamage,
                critical,
                BoomstickSound.ARTILLERY_FIRE,
                false,
                alternateInaccuracyBranch);
    }

    private static ArtilleryWeaponProfile createNobleHandgonneIronBallProfile() {
        return new ArtilleryWeaponProfile(
                NOBLE_HANDGONNE_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                true,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                4.5D,
                9.0F,
                6.5F,
                2.7D,
                true,
                0,
                true,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT,
                false,
                true,
                ArtilleryWeaponProfile.NativeStateMode.STAGED,
                new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1.0D, 210.0D, 0.0D));
    }

    private static ArtilleryWeaponProfile createMarkmengonneIronBallProfile() {
        return new ArtilleryWeaponProfile(
                MARKMENGONNE_ID,
                IRON_BALL_ID,
                IRONBALL_PROJECTILE_ID,
                0,
                false,
                LOADED_STAGE,
                FIRED_STAGE,
                NPC_RELOAD_TICKS,
                NPC_COOLDOWN_TICKS,
                1,
                4.5D,
                7.0F,
                3.3F,
                2.9D,
                false,
                0,
                true,
                BoomstickSound.ARTILLERY_HAND_CANNON_FIRE,
                true,
                true,
                ArtilleryWeaponProfile.InaccuracyBranch.SHIFT,
                false,
                true,
                ArtilleryWeaponProfile.NativeStateMode.STAGED,
                new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1.0D, 130.0D, 5.0D));
    }
}
