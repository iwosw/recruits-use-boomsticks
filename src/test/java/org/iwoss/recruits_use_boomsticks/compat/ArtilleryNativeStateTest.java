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
}
