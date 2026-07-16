package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtilleryWeaponProfileTest {
    @Test
    void exposesOnlyTheConfirmedFirstTrancheWeapons() {
        assertEquals(6, SupportedArtillery.profiles().size());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.HANDGONNE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.ARQUEBUS_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.MATCHLOCK_MUSKET_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.MATCHLOCK_PISTOL_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.TORADAR_RIFLE_ID).isPresent());
        assertTrue(SupportedArtillery.profileFor(SupportedArtillery.MARKMENGONNE_ID).isPresent());
        assertFalse(SupportedArtillery.profileFor("artillery_addon:hand_cannon").isPresent());
    }

    @Test
    void mapsNativeAmmoAndProjectileIdentities() {
        ArtilleryWeaponProfile handgonne = SupportedArtillery
                .profileFor(SupportedArtillery.HANDGONNE_ID)
                .orElseThrow();
        ArtilleryWeaponProfile markmengonne = SupportedArtillery
                .profileFor(SupportedArtillery.MARKMENGONNE_ID)
                .orElseThrow();

        assertEquals(SupportedArtillery.IRON_BALL_ID, handgonne.ammoId());
        assertEquals(SupportedArtillery.IRONBALL_PROJECTILE_ID, handgonne.projectileEntityId());
        assertEquals(0, handgonne.nativeAmmoCode());
        assertTrue(handgonne.usesNativeLoadedFlag());

        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, markmengonne.ammoId());
        assertEquals(SupportedArtillery.VANILLA_ARROW_ID, markmengonne.projectileEntityId());
        assertEquals(2, markmengonne.nativeAmmoCode());
        assertFalse(markmengonne.usesNativeLoadedFlag());
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
}
