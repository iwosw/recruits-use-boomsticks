package org.iwoss.recruits_use_boomsticks.compat;

import java.util.Map;
import net.minecraftforge.fml.ModList;
import static org.iwoss.recruits_use_boomsticks.compat.SupportedArtillery.*;
import static org.iwoss.recruits_use_boomsticks.compat.ArtilleryWeaponProfile.InaccuracyBranch.*;
import static org.iwoss.recruits_use_boomsticks.compat.ArtilleryWeaponProfile.NativeStateMode.*;

/** Constants traced from CurseForge file 8812944; older installations retain their own profiles. */
public final class Artillery1162Profiles {
    private Artillery1162Profiles() {}

    public static boolean isInstalled() {
        return ModList.get() != null && ModList.get().getModContainerById(MOD_ID)
                .map(mod -> mod.getModInfo().getVersion().toString().equals("1.16.2")).orElse(false);
    }

    static void addWheellocks(Map<String, ArtilleryWeaponProfile> profiles) {
        profiles.put(WHEELLOCK_PISTOL_ID, wheellock(WHEELLOCK_PISTOL_ID, SMALL_IRON_BALL_ID, 3, 4, 4, 6, 6, 3, NONE, STAGED));
        profiles.put(WHEELLOCK_MUSKET_ID, wheellock(WHEELLOCK_MUSKET_ID, IRON_BALL_ID, 3, 4, 7, 4, 2, 5.7, FORK_REST, STAGED));
        profiles.put(WHEELLOCK_HUNTING_RIFLE_ID, wheellock(WHEELLOCK_HUNTING_RIFLE_ID, IRON_BALL_ID, 3, 4, 7.4, 3, 1.5F, 6.2, FORK_REST, STAGED));
        profiles.put(WHEELLOCK_BREECHLOADING_RIFLE_ID, wheellock(WHEELLOCK_BREECHLOADING_RIFLE_ID, LOADED_CARTRIDGE_ID, 2, 3, 7.4, 3, 1, 6.2, FORK_REST, STAGED));
        profiles.put(DUAL_WHEELLOCK_PISTOL_ID, wheellock(DUAL_WHEELLOCK_PISTOL_ID, SMALL_IRON_BALL_ID, 4, 0, 4, 6, 6, 3, NONE, WHEELLOCK_DUAL));
        profiles.put(DUAL_WHEELLOCK_CARBINE_ID, wheellock(DUAL_WHEELLOCK_CARBINE_ID, SMALL_IRON_BALL_ID, 4, 0, 5, 5, 2.5F, 3.6, FORK_REST, WHEELLOCK_DUAL));
    }

    private static ArtilleryWeaponProfile wheellock(String id, String ammo, int ready, int fired,
            double speed, float spread, float restedSpread, double damage,
            ArtilleryWeaponProfile.InaccuracyBranch branch, ArtilleryWeaponProfile.NativeStateMode state) {
        // Native roll [1,750) < damage/2, equivalently [2,1500) < damage.
        return new ArtilleryWeaponProfile(id, ammo, IRONBALL_PROJECTILE_ID, 0, false, ready, fired,
                40, 20, 1, speed, spread, restedSpread, damage, false, 0, true,
                BoomstickSound.ARTILLERY_FIRE, false, false, branch, false,
                state == STAGED && !ammo.equals(LOADED_CARTRIDGE_ID), state,
                new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 2, 1500, 0));
    }

    public static ArtilleryWeaponProfile resolve(ArtilleryWeaponProfile p) {
        return isInstalled() ? updated(p) : p;
    }

    public static ArtilleryWeaponProfile mounted(ArtilleryWeaponProfile p) {
        return switch (p.registryId()) {
            case ARQUEBUS_ID -> ballistics(p, 6, 7.5F, 7.5F, 5.5, NONE, 275, -15);
            case MATCHLOCK_MUSKET_ID -> ballistics(p, 6.6, 6.5F, 6.5F, 5.5, NONE, 500, -50);
            case TORADAR_RIFLE_ID -> ballistics(p, 7, 5.5F, 5.5F, 6, NONE, 600, -60);
            case MINI_PISTOLA_ID -> ballistics(p, 2, 7.5F, 7.5F, 3, NONE, 80, 0);
            case WHEELLOCK_MUSKET_ID -> spread(p, 5.5F);
            case WHEELLOCK_HUNTING_RIFLE_ID, WHEELLOCK_BREECHLOADING_RIFLE_ID -> spread(p, 4.5F);
            default -> p;
        };
    }

    private static ArtilleryWeaponProfile spread(ArtilleryWeaponProfile p, float spread) {
        return new ArtilleryWeaponProfile(p.registryId(), p.ammoId(), p.projectileEntityId(), p.nativeAmmoCode(),
                p.usesNativeLoadedFlag(), p.loadedStage(), p.firedStage(), p.reloadTicks(), p.cooldownTicks(),
                p.projectileCount(), p.projectileVelocity(), spread, spread, p.baseDamage(), p.critical(),
                p.pierceLevel(), p.silent(), p.firingSound(), p.usesNativeAmmoCode(), p.nativeAmmoCodeIsDouble(),
                NONE, p.pickupAllowed(), p.usesNativePowderMarker(), p.nativeStateMode(), p.nativeMisfirePolicy());
    }

    /** Standing profiles; mounted overrides are selected at the actual shot boundary. */
    public static ArtilleryWeaponProfile updated(ArtilleryWeaponProfile p) {
        return switch (p.registryId()) {
            case HANDGONNE_ID -> ballistics(p, 3.5, 8, 4, 4, SHIFT, 105, 5);
            case TACCOLA_HANDGONNE_ID, DOUBLE_BARREL_GONNE_ID -> ballistics(p, 3.5, 8, 4, 4, SHIFT, 105, 0);
            case BRONZE_HANDGONNE_ID -> ballistics(p, 3.5, 8, 4, 3.8, SHIFT, 89, 5);
            case TILLER_GUN_ID -> ballistics(p, 2.5, 5, 2.5F, 3, SHIFT, 105, 5);
            case HAND_CANNON_ID -> ballistics(p, 3, 10, 5, 3.5, SHIFT, 205, 15);
            case ARQUEBUS_ID -> ballistics(p, 6, 6, 3, 3.75, FORK_REST, 275, -15);
            case MATCHLOCK_MUSKET_ID -> ballistics(p, 6.6, 5, 2.5F, 5.5, FORK_REST, 500, -50);
            case MATCHLOCK_CARBINE_ID -> ballistics(p, 5.6, 5, 3, 5, FORK_REST, 275, -15);
            case MATCHLOCK_PISTOL_ID -> ballistics(p, 4, 7, 7, 4.5, NONE, 325, -20);
            case TORADAR_RIFLE_ID -> ballistics(p, 7, 4, 2, 6, FORK_REST, 600, -60);
            case MINI_PISTOLA_ID -> ballistics(p, 2, 6, 7.5F, 1, PASSENGER, 80, 0);
            case NOBLE_HANDGONNE_ID -> p.ammoId().equals(IRON_BALL_ID)
                    ? ballistics(p, 3.5, 7, 3.5F, 4, SHIFT, 210, 0)
                    : ballistics(p, 4.5, 8.5F, 4, 3.5, SHIFT, 210, -10);
            case MARKMENGONNE_ID -> p.ammoId().equals(IRON_BALL_ID)
                    ? ballistics(p, 3.6, 6, 3, 4.1, SHIFT, 130, 5)
                    : ballistics(p, 4.5, 6.5F, 4, 1.5, SHIFT, 130, -5);
            case HARQUEBUS_ID -> ballistics(p, 5, 7, 3.5F, 4.5, FORK_REST, 275, -15);
            case HACKBUT_ID -> ballistics(p, 10, 9, 7, 10, SHIFT, 500, -50);
            default -> p;
        };
    }

    private static ArtilleryWeaponProfile ballistics(ArtilleryWeaponProfile p, double speed, float spread,
            float alternate, double damage, ArtilleryWeaponProfile.InaccuracyBranch branch, double max, double offset) {
        return new ArtilleryWeaponProfile(p.registryId(), p.ammoId(), p.projectileEntityId(), p.nativeAmmoCode(),
                p.usesNativeLoadedFlag(), p.loadedStage(), p.firedStage(), p.reloadTicks(), p.cooldownTicks(),
                p.projectileCount(), speed, spread, alternate, damage, false,
                p.registryId().equals(HACKBUT_ID) ? 1 : 0, !p.ammoId().equals(VANILLA_ARROW_ID),
                p.firingSound(), p.usesNativeAmmoCode(),
                p.nativeAmmoCodeIsDouble(), branch, p.pickupAllowed(), p.usesNativePowderMarker(), p.nativeStateMode(),
                new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1, max, offset));
    }
}
