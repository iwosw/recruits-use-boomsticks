package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.iwoss.recruits_use_boomsticks.compat.SupportedArtillery.*;

class Artillery1162ProfilesTest {
    @Test
    void rebalancesExistingWeaponsWithoutChangingTheLegacyCatalog() {
        var legacy = SupportedArtillery.profileFor(MATCHLOCK_MUSKET_ID).orElseThrow();
        var updated = Artillery1162Profiles.updated(legacy);
        assertEquals(7, legacy.projectileVelocity());
        assertEquals(4.5, legacy.baseDamage());
        assertEquals(6.6, updated.projectileVelocity());
        assertEquals(5.5, updated.baseDamage());
        assertEquals(5, updated.inaccuracy());
        assertEquals(2.5, updated.alternateInaccuracy());
        assertEquals(6.5, Artillery1162Profiles.mounted(updated).inaccuracy());
        assertFalse(updated.nativeMisfirePolicy().misfires(1, 20));
        assertTrue(updated.nativeMisfirePolicy().misfires(1, 100));
        for (String id : List.of(NOBLE_HANDGONNE_ID, MARKMENGONNE_ID)) {
            var arrow = Artillery1162Profiles.updated(SupportedArtillery.profileFor(id).orElseThrow());
            assertEquals(VANILLA_ARROW_ID, arrow.ammoId());
            assertFalse(arrow.silent(), "native arrow branches remain audible");
        }
    }

    @Test
    void mountedArquebusAndMiniPistolaUseTheirDistinctNativeDamage() {
        var arquebus = Artillery1162Profiles.updated(SupportedArtillery.profileFor(ARQUEBUS_ID).orElseThrow());
        assertEquals(3.75, arquebus.baseDamage());
        assertEquals(5.5, Artillery1162Profiles.mounted(arquebus).baseDamage());
        var mini = Artillery1162Profiles.updated(SupportedArtillery.profileFor(MINI_PISTOLA_ID).orElseThrow());
        assertEquals(1, mini.baseDamage());
        assertEquals(3, Artillery1162Profiles.mounted(mini).baseDamage());
    }

    @Test
    void doubleWheellocksSpendTwoBallsAndFireSecondBarrelBeforeFirst() {
        for (String id : List.of(DUAL_WHEELLOCK_PISTOL_ID, DUAL_WHEELLOCK_CARBINE_ID)) {
            var profile = SupportedArtillery.profileFor(id).orElseThrow();
            var tag = new CompoundTag();
            for (var step : ArtilleryReloadProtocol.stepsFor(profile)) ArtilleryNativeState.applyReloadStepTag(tag, step);
            assertEquals(2, ArtilleryReloadProtocol.ammoConsumed(profile));
            assertEquals(2, ArtilleryNativeState.remainingRoundsTag(tag, profile));
            assertEquals(Tag.TAG_DOUBLE, tag.getTagType("loaded"));
            assertFalse(tag.contains("powder"));
            assertFalse(tag.contains("stage"));
            ArtilleryNativeState.markFiredTag(tag, profile);
            assertEquals(0, tag.getDouble("barrel_two"));
            assertEquals(4, tag.getDouble("barrel_one"));
            assertEquals(1, ArtilleryNativeState.remainingRoundsTag(tag, profile));
            var restored = tag.copy();
            ArtilleryNativeState.markFiredTag(restored, profile);
            assertFalse(ArtilleryNativeState.isLoadedTag(restored, profile));
            assertEquals(0, ArtilleryNativeState.completedReloadStepsTag(restored, ArtilleryReloadProtocol.stepsFor(profile)));
        }
    }

    @Test
    void breechloaderSpendsACartridgeAndRequiresNoLoosePowderOrRamrod() {
        var p = SupportedArtillery.profileFor(WHEELLOCK_BREECHLOADING_RIFLE_ID).orElseThrow();
        var chain = ArtilleryReloadProtocol.stepsFor(p);
        assertEquals(2, chain.size());
        assertEquals(LOADED_CARTRIDGE_ID, chain.get(0).components().get(0).id());
        assertEquals(ArtilleryReloadProtocol.SPANNER_TAG, chain.get(1).components().get(0).id());
        assertEquals(1, ArtilleryReloadProtocol.ammoConsumed(p));
        var tag = new CompoundTag();
        chain.forEach(step -> ArtilleryNativeState.applyReloadStepTag(tag, step));
        assertTrue(ArtilleryNativeState.isLoadedTag(tag, p));
        assertFalse(tag.contains("powder"));
        ArtilleryNativeState.markFiredTag(tag, p);
        assertEquals(3, tag.getDouble("stage"));
        assertFalse(ArtilleryNativeState.isLoadedTag(tag, p));
    }

    @Test
    void handMortarAndFireLanceAreEnabledWhileMountedWallgunRemainsOutsideTheAllowlist() {
        assertTrue(SupportedArtillery.isGameplayWeapon(WHEELLOCK_HAND_MORTAR_ID));
        assertTrue(SupportedArtillery.isGameplayWeapon(FIRE_LANCE_ID));
        assertFalse(SupportedArtillery.isGameplayWeapon(MOD_ID + ":wheellock_wallgun"));
    }

    @Test
    void mortarAndFireLanceBranchesReachTheirNativeReadyState() {
        for (var profile : List.of(Artillery1162Profiles.handMortar(false),
                Artillery1162Profiles.handMortar(true), Artillery1162Profiles.fireLance(false),
                Artillery1162Profiles.fireLance(true))) {
            var tag = new CompoundTag();
            var steps = ArtilleryReloadProtocol.stepsFor(profile);
            steps.forEach(step -> ArtilleryNativeState.applyReloadStepTag(tag, step));
            assertTrue(ArtilleryNativeState.isLoadedTag(tag, profile), profile.ammoId());
            assertEquals(profile.loadedStage(), tag.getDouble("stage"));
            assertEquals(profile.ammoId().equals("minecraft:air") ? 0 : 1,
                    ArtilleryReloadProtocol.ammoConsumed(profile));
            ArtilleryNativeState.markFiredTag(tag, profile);
            assertFalse(ArtilleryNativeState.isLoadedTag(tag, profile));
            if (FIRE_LANCE_ID.equals(profile.registryId())) {
                assertEquals(0, tag.getDouble("ammo"));
            }
        }
    }
}
