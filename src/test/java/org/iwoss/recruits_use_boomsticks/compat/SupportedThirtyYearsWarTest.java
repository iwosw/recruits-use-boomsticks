package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupportedThirtyYearsWarTest {
    @Test
    void claimsOnlyTheTwoVerifiedNativeGunIds() {
        assertEquals(2, SupportedThirtyYearsWar.WEAPON_IDS.size());
        assertTrue(SupportedThirtyYearsWar.isWeaponId("thirtyyearswar:baroque_matchlock_musket"));
        assertTrue(SupportedThirtyYearsWar.isWeaponId("thirtyyearswar:baroque_wheellock_pistol"));
        assertFalse(SupportedThirtyYearsWar.isWeaponId("artillery_addon:matchlock_musket"));
        assertFalse(SupportedThirtyYearsWar.isWeaponId("thirtyyearswar:steel_rapier"));
        assertFalse(SupportedThirtyYearsWar.isWeaponId(null));
    }

    @Test
    void cartridgesCannotOverlapArtilleryOrVanillaAmmo() {
        assertTrue(SupportedThirtyYearsWar.isAmmoId("thirtyyearswar:paper_cartridge"));
        assertFalse(SupportedThirtyYearsWar.isAmmoId("artillery_addon:loaded_cartridge"));
        assertFalse(SupportedThirtyYearsWar.isAmmoId("minecraft:arrow"));
        assertFalse(SupportedThirtyYearsWar.isAmmoId(null));
    }
}
