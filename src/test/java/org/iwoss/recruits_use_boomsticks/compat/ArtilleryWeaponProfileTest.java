package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtilleryWeaponProfileTest {
    @Test
    void exposesOnlyTheConfirmedGameplayProfilesAndReconnaissanceEntries() {
        assertEquals(24, SupportedArtillery.profiles().size());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.HANDGONNE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.ARQUEBUS_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.MATCHLOCK_MUSKET_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.MATCHLOCK_PISTOL_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.TORADAR_RIFLE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.TILLER_GUN_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.NOBLE_HANDGONNE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.MARKMENGONNE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.BRONZE_HANDGONNE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.MINI_PISTOLA_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.HARQUEBUS_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.HACKBUT_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.WINDLASS_CROSSBOW_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.TACCOLA_HANDGONNE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.HAND_CANNON_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.DOUBLE_BARREL_GONNE_ID).isPresent());
    }

    @Test
    void exposesTheNarrowChuKoNuArrowShotProfile() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor("artillery_addon:chu_ko_nu")
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon("artillery_addon:chu_ko_nu"));
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.ammoId());
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.projectileEntityId());
        // ChuKoNuShootProcedure counts its magazine up to the native eight rounds.
        assertEquals(8, profile.nativeAmmoCode());
        assertEquals(8, profile.magazineSize());
        assertEquals(1, profile.projectileCount());
        assertEquals(8, profile.ammoPerReload());
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        assertFalse(profile.usesNativeStageMarker());
        assertEquals(2.4D, profile.projectileVelocity());
        assertEquals(0.5F, profile.inaccuracy());
        assertEquals(1.6D, profile.baseDamage());
        assertTrue(profile.critical());
        assertEquals(0, profile.pierceLevel());
        assertFalse(profile.silent());
        assertTrue(profile.pickupAllowed());
        assertEquals(BoomstickSound.CROSSBOW_SHOOT, profile.firingSound());
    }

    @Test
    void keepsEverySingleShotProtocolAtOneRoundPerReload() {
        ArtilleryWeaponProfile handCannon = SupportedArtillery
                .profileFor(SupportedArtillery.HAND_CANNON_ID)
                .orElseThrow();

        // A multi-projectile volley is still one round; repeaters and dual barrels hold more.
        assertEquals(1, handCannon.magazineSize());
        assertEquals(3, handCannon.projectileCount());
        assertEquals(3, handCannon.ammoPerReload());

        SupportedArtillery.profiles().values().stream()
                .filter(profile -> profile.nativeStateMode()
                        != ArtilleryWeaponProfile.NativeStateMode.AMMO_COUNT)
                .filter(profile -> profile.nativeStateMode()
                        != ArtilleryWeaponProfile.NativeStateMode.WHEELLOCK_DUAL)
                .forEach(profile -> {
                    assertEquals(1, profile.magazineSize(), profile.registryId());
                    assertEquals(profile.projectileCount(), profile.ammoPerReload(), profile.registryId());
                });
    }

    @Test
    void mapsNativeAmmoAndProjectileIdentities() {
        ArtilleryWeaponProfile handgonne = SupportedArtillery
                .profileFor(SupportedArtillery.HANDGONNE_ID)
                .orElseThrow();
        ArtilleryWeaponProfile markmengonne = SupportedArtillery
                .profileFor(SupportedArtillery.MARKMENGONNE_ID)
                .orElseThrow();
        ArtilleryWeaponProfile miniPistola = SupportedArtillery
                .profileFor(SupportedArtillery.MINI_PISTOLA_ID)
                .orElseThrow();

        assertEquals(SupportedArtillery.IRON_BALL_ID, handgonne.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, handgonne.projectileEntityId());
        assertEquals(0, handgonne.nativeAmmoCode());
        assertTrue(handgonne.usesNativeLoadedFlag());

        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, markmengonne.ammoId());
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, markmengonne.projectileEntityId());
        assertEquals(2, markmengonne.nativeAmmoCode());
        assertTrue(markmengonne.usesNativeAmmoCode());
        assertTrue(markmengonne.nativeAmmoCodeIsDouble());
        assertFalse(markmengonne.usesNativeLoadedFlag());

        assertEquals(SupportedArtillery.SMALL_IRON_BALL_ID, miniPistola.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, miniPistola.projectileEntityId());
        assertEquals(1, miniPistola.loadedStage());
        assertEquals(3, miniPistola.firedStage());
        assertEquals(3.3D, miniPistola.projectileVelocity());
        assertEquals(10.0F, miniPistola.inaccuracy());
        assertEquals(11.0F, miniPistola.alternateInaccuracy());
        assertEquals(
                ArtilleryWeaponProfile.InaccuracyBranch.PASSENGER,
                miniPistola.alternateInaccuracyBranch());
        assertEquals(1.0D, miniPistola.baseDamage());
        assertFalse(miniPistola.critical());
        assertFalse(miniPistola.usesNativeLoadedFlag());
        assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_FIRE, miniPistola.firingSound());
    }

    @Test
    void preservesTheNativeLoadingPhaseInTheGenericProfile() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.ARQUEBUS_ID)
                .orElseThrow();

        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertFalse(profile.usesNativeAmmoCode());
        assertTrue(profile.reloadTicks() > 0);
        assertEquals(BoomstickSound.ARTILLERY_FIRE, profile.toBoomstickProfile().sound());
    }

    @Test
    void keepsTheArquebusProjectileNonCriticalLikeTheNativeProcedure() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.ARQUEBUS_ID)
                .orElseThrow();

        assertFalse(profile.critical());
    }

    @Test
    void retainsTheMatchlockMusketWhenTheConfirmedGameplaySlicesAreEnabled() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.MATCHLOCK_MUSKET_ID)
                .orElseThrow();

        assertEquals(
                java.util.Set.of(
                        SupportedArtillery.HANDGONNE_ID,
                        SupportedArtillery.ARQUEBUS_ID,
                        SupportedArtillery.MATCHLOCK_MUSKET_ID,
                        SupportedArtillery.MATCHLOCK_CARBINE_ID,
                        SupportedArtillery.MATCHLOCK_PISTOL_ID,
                        SupportedArtillery.TORADAR_RIFLE_ID,
                        SupportedArtillery.MINI_PISTOLA_ID,
                        SupportedArtillery.TILLER_GUN_ID,
                        SupportedArtillery.NOBLE_HANDGONNE_ID,
                        SupportedArtillery.MARKMENGONNE_ID,
                        SupportedArtillery.BRONZE_HANDGONNE_ID,
                        SupportedArtillery.HARQUEBUS_ID,
                        SupportedArtillery.HACKBUT_ID,
                        SupportedArtillery.WINDLASS_CROSSBOW_ID,
                        SupportedArtillery.CHU_KO_NU_ID,
                        SupportedArtillery.TACCOLA_HANDGONNE_ID,
                        SupportedArtillery.HAND_CANNON_ID,
                        SupportedArtillery.DOUBLE_BARREL_GONNE_ID,
                        SupportedArtillery.WHEELLOCK_PISTOL_ID, SupportedArtillery.WHEELLOCK_MUSKET_ID,
                        SupportedArtillery.WHEELLOCK_HUNTING_RIFLE_ID, SupportedArtillery.WHEELLOCK_BREECHLOADING_RIFLE_ID,
                        SupportedArtillery.DUAL_WHEELLOCK_PISTOL_ID, SupportedArtillery.DUAL_WHEELLOCK_CARBINE_ID
                ),
                SupportedArtillery.gameplayWeaponIds());
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.ARQUEBUS_ID));
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.MATCHLOCK_MUSKET_ID));
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.MATCHLOCK_PISTOL_ID));
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.TORADAR_RIFLE_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(7.0D, profile.projectileVelocity());
        assertEquals(5.0F, profile.inaccuracy());
        assertEquals(4.5D, profile.baseDamage());
        assertFalse(profile.critical());
    }

    @Test
    void enablesMatchlockCarbineForItsStandingAndForkRestIronballBranches() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor("artillery_addon:matchlock_carbine")
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon("artillery_addon:matchlock_carbine"));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        // The native Carbine chain runs stage 1.0, 2.0, 3.0 and fires at stage 3.0.
        assertEquals(3, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(6.5D, profile.projectileVelocity());
        assertEquals(6.0F, profile.inaccuracy());
        assertEquals(3.5F, profile.alternateInaccuracy());
        assertEquals("FORK_REST", profile.alternateInaccuracyBranch().name());
        assertEquals(2.7D, profile.baseDamage());
        assertFalse(profile.critical());
        assertFalse(profile.usesNativeLoadedFlag());
        assertFalse(profile.usesNativeAmmoCode());
    }

    @Test
    void retainsTheMatchlockPistolWhenTheConfirmedGameplaySlicesAreEnabled() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.MATCHLOCK_PISTOL_ID)
                .orElseThrow();

        assertEquals(
                java.util.Set.of(
                        SupportedArtillery.HANDGONNE_ID,
                        SupportedArtillery.ARQUEBUS_ID,
                        SupportedArtillery.MATCHLOCK_MUSKET_ID,
                        SupportedArtillery.MATCHLOCK_CARBINE_ID,
                        SupportedArtillery.MATCHLOCK_PISTOL_ID,
                        SupportedArtillery.TORADAR_RIFLE_ID,
                        SupportedArtillery.MINI_PISTOLA_ID,
                        SupportedArtillery.TILLER_GUN_ID,
                        SupportedArtillery.NOBLE_HANDGONNE_ID,
                        SupportedArtillery.MARKMENGONNE_ID,
                        SupportedArtillery.BRONZE_HANDGONNE_ID,
                        SupportedArtillery.HARQUEBUS_ID,
                        SupportedArtillery.HACKBUT_ID,
                        SupportedArtillery.WINDLASS_CROSSBOW_ID,
                        SupportedArtillery.CHU_KO_NU_ID,
                        SupportedArtillery.TACCOLA_HANDGONNE_ID,
                        SupportedArtillery.HAND_CANNON_ID,
                        SupportedArtillery.DOUBLE_BARREL_GONNE_ID,
                        SupportedArtillery.WHEELLOCK_PISTOL_ID, SupportedArtillery.WHEELLOCK_MUSKET_ID,
                        SupportedArtillery.WHEELLOCK_HUNTING_RIFLE_ID, SupportedArtillery.WHEELLOCK_BREECHLOADING_RIFLE_ID,
                        SupportedArtillery.DUAL_WHEELLOCK_PISTOL_ID, SupportedArtillery.DUAL_WHEELLOCK_CARBINE_ID
                ),
                SupportedArtillery.gameplayWeaponIds());
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.MATCHLOCK_PISTOL_ID));
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.TORADAR_RIFLE_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(6.0D, profile.projectileVelocity());
        assertEquals(6.0F, profile.inaccuracy());
        assertEquals(1.9D, profile.baseDamage());
        assertTrue(profile.critical());
    }

    @Test
    void retainsTheToradarRifleInTheConfirmedGameplaySlices() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.TORADAR_RIFLE_ID)
                .orElseThrow();

        assertEquals(
                java.util.Set.of(
                        SupportedArtillery.HANDGONNE_ID,
                        SupportedArtillery.ARQUEBUS_ID,
                        SupportedArtillery.MATCHLOCK_MUSKET_ID,
                        SupportedArtillery.MATCHLOCK_CARBINE_ID,
                        SupportedArtillery.MATCHLOCK_PISTOL_ID,
                        SupportedArtillery.TORADAR_RIFLE_ID,
                        SupportedArtillery.MINI_PISTOLA_ID,
                        SupportedArtillery.TILLER_GUN_ID,
                        SupportedArtillery.NOBLE_HANDGONNE_ID,
                        SupportedArtillery.MARKMENGONNE_ID,
                        SupportedArtillery.BRONZE_HANDGONNE_ID,
                        SupportedArtillery.HARQUEBUS_ID,
                        SupportedArtillery.HACKBUT_ID,
                        SupportedArtillery.WINDLASS_CROSSBOW_ID,
                        SupportedArtillery.CHU_KO_NU_ID,
                        SupportedArtillery.TACCOLA_HANDGONNE_ID,
                        SupportedArtillery.HAND_CANNON_ID,
                        SupportedArtillery.DOUBLE_BARREL_GONNE_ID,
                        SupportedArtillery.WHEELLOCK_PISTOL_ID, SupportedArtillery.WHEELLOCK_MUSKET_ID,
                        SupportedArtillery.WHEELLOCK_HUNTING_RIFLE_ID, SupportedArtillery.WHEELLOCK_BREECHLOADING_RIFLE_ID,
                        SupportedArtillery.DUAL_WHEELLOCK_PISTOL_ID, SupportedArtillery.DUAL_WHEELLOCK_CARBINE_ID
                ),
                SupportedArtillery.gameplayWeaponIds());
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.TORADAR_RIFLE_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(7.0D, profile.projectileVelocity());
        assertEquals(6.0F, profile.inaccuracy());
        assertEquals(3.2D, profile.baseDamage());
        assertFalse(profile.critical());
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.HANDGONNE_ID));
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.MARKMENGONNE_ID));
    }

    @Test
    void enablesTillerGunAsTheSixthNarrowGameplaySlice() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.TILLER_GUN_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.TILLER_GUN_ID));
        assertEquals(SupportedArtillery.SMALL_IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(3.4D, profile.projectileVelocity());
        assertEquals(8.0F, profile.inaccuracy());
        assertEquals(4.0F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(2.0D, profile.baseDamage());
        assertFalse(profile.critical());
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.HANDGONNE_ID));
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.MARKMENGONNE_ID));
    }

    @Test
    void enablesHandgonneWithTheConfirmedIronballProfile() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HANDGONNE_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.HANDGONNE_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertTrue(profile.usesNativeLoadedFlag());
        // The native ball step writes the double `ammo=0.0` marker.
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        assertEquals(0, profile.nativeAmmoCode());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(9.0F, profile.inaccuracy());
        assertEquals(6.5F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(1.85D, profile.baseDamage());
        assertFalse(profile.critical());
        assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_FIRE, profile.firingSound());
    }

    @Test
    void enablesTaccolaOnlyForItsConfirmedIronBallBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.TACCOLA_HANDGONNE_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.TACCOLA_HANDGONNE_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(0, profile.nativeAmmoCode());
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        assertTrue(profile.usesNativeLoadedFlag());
        assertTrue(profile.usesNativePowderMarker());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(9.0F, profile.inaccuracy());
        assertEquals(6.5F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(1.85D, profile.baseDamage());
        assertFalse(profile.critical());
        assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_FIRE, profile.firingSound());
    }

    @Test
    void enablesNobleHandgonneOnlyForItsConfirmedArrowBranch() {
        var profileResult = SupportedArtillery.profileFor("artillery_addon:noble_handgonne");
        assertTrue(profileResult.isPresent());
        ArtilleryWeaponProfile profile = profileResult.orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon("artillery_addon:noble_handgonne"));
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.ammoId());
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.projectileEntityId());
        assertEquals(2, profile.nativeAmmoCode());
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        // The native Arrow branch never runs the ramming step that writes a `loaded` flag.
        assertFalse(profile.usesNativeLoadedFlag());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(8.5F, profile.inaccuracy());
        assertEquals(4.0F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(2.55D, profile.baseDamage());
        assertFalse(profile.critical());
        assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_FIRE, profile.firingSound());
        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.MARKMENGONNE_ID));
    }

    @Test
    void enablesMarkmengonneOnlyForItsConfirmedArrowBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.MARKMENGONNE_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.MARKMENGONNE_ID));
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.ammoId());
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.projectileEntityId());
        assertEquals(2, profile.nativeAmmoCode());
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        assertFalse(profile.usesNativeLoadedFlag());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(6.5F, profile.inaccuracy());
        assertEquals(4.0F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(1.5D, profile.baseDamage());
        assertFalse(profile.critical());
        assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_FIRE, profile.firingSound());
    }

    @Test
    void exposesMarkmengonneConfirmedIronBallBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery.markmengonneIronBallProfile();

        assertEquals(SupportedArtillery.MARKMENGONNE_ID, profile.registryId());
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(0, profile.nativeAmmoCode());
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        assertFalse(profile.usesNativeLoadedFlag());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(7.0F, profile.inaccuracy());
        assertEquals(3.3F, profile.alternateInaccuracy());
        assertEquals(2.9D, profile.baseDamage());
        assertFalse(profile.critical());
        assertTrue(profile.silent());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1.0D, 130.0D, 5.0D),
                profile.nativeMisfirePolicy());
    }

    @Test
    void enablesBronzeHandgonneForItsConfirmedIronBallBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.BRONZE_HANDGONNE_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.BRONZE_HANDGONNE_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(0, profile.nativeAmmoCode());
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        assertTrue(profile.usesNativeLoadedFlag());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(9.0F, profile.inaccuracy());
        assertEquals(4.5F, profile.alternateInaccuracy());
        assertEquals(1.85D, profile.baseDamage());
        assertFalse(profile.critical());
        assertTrue(profile.silent());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(new ArtilleryWeaponProfile.NativeMisfirePolicy(true, 1.0D, 89.0D, 5.0D),
                profile.nativeMisfirePolicy());
    }

    @Test
    void enablesHarquebusForItsStandingAndForkRestIronballBranches() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HARQUEBUS_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.HARQUEBUS_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        // The native Harquebus chain runs stage 1.0, 2.0, 3.0 and fires at stage 3.0.
        assertEquals(3, profile.loadedStage());
        assertEquals(0, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(5.5D, profile.projectileVelocity());
        assertEquals(6.0F, profile.inaccuracy());
        assertEquals(3.5F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.FORK_REST, profile.alternateInaccuracyBranch());
        assertEquals(2.2D, profile.baseDamage());
        assertFalse(profile.critical());
        assertFalse(profile.usesNativeLoadedFlag());
        assertFalse(profile.usesNativeAmmoCode());
        assertEquals(BoomstickSound.ARTILLERY_FIRE, profile.firingSound());
    }

    @Test
    void enablesHackbutOnlyForItsOrdinaryLargeBallBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HACKBUT_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.HACKBUT_ID));
        assertEquals(SupportedArtillery.LARGE_IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(6.5D, profile.projectileVelocity());
        assertEquals(5.5F, profile.inaccuracy());
        assertEquals(5.5F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.NONE, profile.alternateInaccuracyBranch());
        assertEquals(5.25D, profile.baseDamage());
        assertTrue(profile.critical());
        assertEquals(1, profile.pierceLevel());
        assertFalse(profile.silent());
        assertFalse(profile.usesNativeLoadedFlag());
        assertFalse(profile.usesNativeAmmoCode());
        assertEquals(BoomstickSound.ARTILLERY_FIRE, profile.firingSound());
    }

    @Test
    void enablesWindlassCrossbowOnlyForItsConfirmedArrowStageFourBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.WINDLASS_CROSSBOW_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.WINDLASS_CROSSBOW_ID));
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.ammoId());
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, profile.projectileEntityId());
        assertEquals(4, profile.loadedStage());
        assertEquals(0, profile.firedStage());
        assertEquals(1, profile.projectileCount());
        assertEquals(3.5D, profile.projectileVelocity());
        assertEquals(1.0F, profile.inaccuracy());
        assertEquals(2.6D, profile.baseDamage());
        assertTrue(profile.critical());
        assertEquals(BoomstickSound.CROSSBOW_SHOOT, profile.firingSound());
        assertTrue(profile.pickupAllowed());
        assertFalse(profile.usesNativePowderMarker());
        assertFalse(profile.usesNativeAmmoCode());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.NONE, profile.alternateInaccuracyBranch());
    }

    @Test
    void enablesHandCannonAsAConfirmedThreeBallIronballVolley() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HAND_CANNON_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.HAND_CANNON_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(3, profile.projectileCount());
        assertEquals(3, profile.toBoomstickProfile().ammoPerVolley());
        assertEquals(2, profile.loadedStage());
        assertEquals(3, profile.firedStage());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(10.0F, profile.inaccuracy());
        assertEquals(7.0F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(2.0D, profile.baseDamage());
        assertFalse(profile.critical());
        assertEquals(0, profile.pierceLevel());
        assertTrue(profile.silent());
        assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_FIRE, profile.firingSound());
        assertTrue(profile.usesNativePowderMarker());
        assertTrue(profile.nativeMisfirePolicy().enabled());
        assertEquals(1.0D, profile.nativeMisfirePolicy().randomMinimum());
        assertEquals(205.0D, profile.nativeMisfirePolicy().randomMaximum());
        assertEquals(15.0D, profile.nativeMisfirePolicy().damageThresholdOffset());
    }

    @Test
    void enablesDoubleBarrelGonneOnlyForItsFirstBarrelIronballBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.DOUBLE_BARREL_GONNE_ID)
                .orElseThrow();

        assertTrue(SupportedArtillery.isGameplayWeapon(SupportedArtillery.DOUBLE_BARREL_GONNE_ID));
        assertEquals(SupportedArtillery.IRON_BALL_ID, profile.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, profile.projectileEntityId());
        assertEquals(2, profile.nativeAmmoCode());
        assertTrue(profile.usesNativeAmmoCode());
        assertTrue(profile.nativeAmmoCodeIsDouble());
        assertFalse(profile.usesNativeLoadedFlag());
        assertEquals(1, profile.loadedStage());
        assertEquals(0, profile.firedStage());
        assertEquals(ArtilleryWeaponProfile.NativeStateMode.DOUBLE_BARREL_FIRST, profile.nativeStateMode());
        assertEquals(1, profile.projectileCount());
        assertEquals(4.5D, profile.projectileVelocity());
        assertEquals(9.0F, profile.inaccuracy());
        assertEquals(4.5F, profile.alternateInaccuracy());
        assertEquals(ArtilleryWeaponProfile.InaccuracyBranch.SHIFT, profile.alternateInaccuracyBranch());
        assertEquals(2.7D, profile.baseDamage());
        assertTrue(profile.critical());
        assertEquals(1, profile.pierceLevel());
        assertFalse(profile.silent());
        assertFalse(profile.pickupAllowed());
        assertFalse(profile.usesNativePowderMarker());
        assertTrue(profile.nativeMisfirePolicy().enabled());
        assertEquals(1.0D, profile.nativeMisfirePolicy().randomMinimum());
        assertEquals(105.0D, profile.nativeMisfirePolicy().randomMaximum());
        assertEquals(0.0D, profile.nativeMisfirePolicy().damageThresholdOffset());
        assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_FIRE, profile.firingSound());
    }
}
