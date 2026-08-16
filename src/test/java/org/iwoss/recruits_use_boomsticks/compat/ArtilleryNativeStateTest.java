package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtilleryNativeStateTest {
    @Test
    void loadsAndFiresWithoutUsingPlayerOnlyState() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.ARQUEBUS_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        CompoundTag loaded = weapon;
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(1.0D, loaded.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, loaded.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, loaded.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertFalse(loaded.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(loaded.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        CompoundTag fired = weapon;
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, fired.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, fired.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, fired.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertFalse(fired.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(fired.contains(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void rejectsLoadedStateWithAnInventedNativeAmmoMarker() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.ARQUEBUS_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        weapon.putInt(ArtilleryNativeState.AMMO_KEY, profile.nativeAmmoCode() + 1);

        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
    }

    @Test
    void acceptsTheNativeArquebusStateWhenTheOptionalAmmoMarkerIsAbsent() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.ARQUEBUS_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();
        weapon.putDouble(ArtilleryNativeState.POWDER_KEY, 1.0D);
        weapon.putDouble(ArtilleryNativeState.STAGE_KEY, profile.loadedStage());

        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
    }

    @Test
    void acceptsAValidOptionalArquebusAmmoMarkerButRejectsItsWrongType() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.ARQUEBUS_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();
        ArtilleryNativeState.markLoadedTag(weapon, profile);
        weapon.putInt(ArtilleryNativeState.AMMO_KEY, profile.nativeAmmoCode());

        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));

        weapon.putDouble(ArtilleryNativeState.AMMO_KEY, profile.nativeAmmoCode());
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
    }

    @Test
    void rejectsIntegralValuesThatDoNotMatchTheNativeDoubleProtocol() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.ARQUEBUS_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();
        weapon.putInt(ArtilleryNativeState.POWDER_KEY, 1);
        weapon.putInt(ArtilleryNativeState.STAGE_KEY, profile.loadedStage());

        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
    }

    @Test
    void matchlockMusketUsesTheConfirmedStagedDoubleProtocol() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.MATCHLOCK_MUSKET_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
    }

    @Test
    void matchlockCarbineUsesTheConfirmedStandingStagedDoubleProtocol() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor("artillery_addon:matchlock_carbine")
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        // CarbineRightclickProcedure loads through stages 1.0, 2.0, 3.0 and fires at stage 3.0.
        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
    }

    @Test
    void matchlockPistolUsesTheConfirmedStagedDoubleProtocol() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.MATCHLOCK_PISTOL_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
    }

    @Test
    void toradarRifleUsesTheConfirmedStagedDoubleProtocol() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.TORADAR_RIFLE_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
    }

    @Test
    void miniPistolaUsesStageOneWithoutInventingTheOptionalLoadedFlag() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor("artillery_addon:mini_pistola")
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void tillerGunUsesTheConfirmedStageTwoDoubleProtocol() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.TILLER_GUN_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        // The native ramming step writes `loaded=true` and the ball step the double `ammo=0.0`.
        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertTrue(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void handgonneUsesTheNativeLoadedFlagAtItsStageTwoBoundary() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HANDGONNE_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertTrue(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));
        // TestgunRightclickedProcedure writes the double `ammo=0.0` ball marker.
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void taccolaUsesTheNativeDoubleZeroAmmoMarkerAlongsideItsLoadedFlag() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.TACCOLA_HANDGONNE_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertTrue(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void nobleHandgonneUsesTheNativeDoubleArrowMarkerAtItsLoadedBoundary() {
        var profileResult = SupportedArtillery.profileFor("artillery_addon:noble_handgonne");
        assertTrue(profileResult.isPresent());
        ArtilleryWeaponProfile profile = profileResult.orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        // The native Arrow branch reaches its firing stage without running the ramming step, so no
        // `loaded` flag is written for it.
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void markmengonneUsesTheNativeDoubleArrowMarkerWithoutInventingLoadedFlag() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.MARKMENGONNE_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void markmengonneIronBallBranchUsesAmmoZeroWithoutLoadedFlag() {
        ArtilleryWeaponProfile profile = SupportedArtillery.markmengonneIronBallProfile();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
    }

    @Test
    void bronzeHandgonneUsesItsNativeLoadedFlagAndAmmoZero() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.BRONZE_HANDGONNE_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertTrue(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.getBoolean(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void harquebusUsesTheStagedDoubleProtocolForStandingAndForkRestBranches() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HARQUEBUS_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        // HarquebusRightclick2Procedure loads through stages 1.0, 2.0, 3.0 and fires at stage 3.0,
        // then clears the weapon to stage 0.0.
        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
    }

    @Test
    void hackbutUsesTheLargeBallStandingStagedDoubleProtocolWithoutForkRestState() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HACKBUT_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void windlassCrossbowUsesStageFourWithoutInventingPowderState() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.WINDLASS_CROSSBOW_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(4.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.POWDER_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.POWDER_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
    }

    @Test
    void chuKoNuWalksTheNativeDoubleAmmoCounterDownWithoutStageOrPowderState() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.CHU_KO_NU_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(8.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertEquals(8, ArtilleryNativeState.remainingRoundsTag(weapon, profile));
        assertFalse(weapon.contains(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.POWDER_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        // The confirmed native counter runs 8.0 down to 0.0 and only the last shot unloads.
        for (int round = 7; round >= 1; round--) {
            ArtilleryNativeState.markFiredTag(weapon, profile);
            assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
            assertEquals((double) round, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
            assertEquals(round, ArtilleryNativeState.remainingRoundsTag(weapon, profile));
        }

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.AMMO_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
        assertEquals(0, ArtilleryNativeState.remainingRoundsTag(weapon, profile));
        assertFalse(weapon.contains(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.POWDER_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        // An empty counter must never underflow into a negative native value.
        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.AMMO_KEY));
    }

    @Test
    void chuKoNuRejectsCounterValuesOutsideTheNativeMagazineBoundary() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.CHU_KO_NU_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        weapon.putDouble(ArtilleryNativeState.AMMO_KEY, 9.0D);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0, ArtilleryNativeState.remainingRoundsTag(weapon, profile));

        weapon.putDouble(ArtilleryNativeState.AMMO_KEY, 1.5D);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0, ArtilleryNativeState.remainingRoundsTag(weapon, profile));

        weapon.putInt(ArtilleryNativeState.AMMO_KEY, 2);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0, ArtilleryNativeState.remainingRoundsTag(weapon, profile));

        weapon.putDouble(ArtilleryNativeState.AMMO_KEY, 2.0D);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(2, ArtilleryNativeState.remainingRoundsTag(weapon, profile));
    }

    @Test
    void handCannonUsesTheThreeBallStagedDoubleProtocol() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.HAND_CANNON_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.POWDER_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.STAGE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.POWDER_KEY));
        assertEquals(3.0D, weapon.getDouble(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void doubleBarrelGonneUsesOnlyTheFirstBarrelNativeIronballBoundary() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.DOUBLE_BARREL_GONNE_ID)
                .orElseThrow();
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markLoadedTag(weapon, profile);
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.BARREL_ONE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.RAMMED_ONE_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.BARREL_TWO_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.RAMMED_TWO_KEY));
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.LOADED_KEY));
        assertEquals(2.0D, weapon.getDouble(ArtilleryNativeState.BARREL_ONE_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.RAMMED_ONE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.BARREL_TWO_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.RAMMED_TWO_KEY));
        assertEquals(1.0D, weapon.getDouble(ArtilleryNativeState.LOADED_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.POWDER_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));

        ArtilleryNativeState.markFiredTag(weapon, profile);
        assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.BARREL_ONE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.RAMMED_ONE_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.BARREL_TWO_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.RAMMED_TWO_KEY));
        assertEquals(0.0D, weapon.getDouble(ArtilleryNativeState.LOADED_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.POWDER_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.STAGE_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.AMMO_KEY));
    }
}
