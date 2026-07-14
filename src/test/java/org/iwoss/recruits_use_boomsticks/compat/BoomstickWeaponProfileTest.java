package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoomstickWeaponProfileTest {
    @Test
    void catalogContainsExactlyTheSupportedWeapons() {
        assertEquals(Set.of(
                "medieval_boomsticks:handgonne",
                "medieval_boomsticks:spikedhandgonne",
                "medieval_boomsticks:arquebus",
                "medieval_boomsticks:arbalest"
        ), SupportedBoomsticks.supportedWeaponIds());
    }

    @Test
    void profilesUseTheCorrectAmmoFamilies() {
        assertEquals(BoomstickAmmoType.ROUND_BALL,
                SupportedBoomsticks.profileFor("medieval_boomsticks:handgonne").orElseThrow().ammoType());
        assertEquals(BoomstickAmmoType.ROUND_BALL,
                SupportedBoomsticks.profileFor("medieval_boomsticks:spikedhandgonne").orElseThrow().ammoType());
        assertEquals(BoomstickAmmoType.ROUND_BALL,
                SupportedBoomsticks.profileFor("medieval_boomsticks:arquebus").orElseThrow().ammoType());
        assertEquals(BoomstickAmmoType.HEAVY_BOLT,
                SupportedBoomsticks.profileFor("medieval_boomsticks:arbalest").orElseThrow().ammoType());
    }

    @Test
    void profilesPreserveConfirmedWeaponCharacteristics() {
        BoomstickWeaponProfile handgonne = SupportedBoomsticks
                .profileFor("medieval_boomsticks:handgonne")
                .orElseThrow();
        BoomstickWeaponProfile spikedHandgonne = SupportedBoomsticks
                .profileFor("medieval_boomsticks:spikedhandgonne")
                .orElseThrow();
        BoomstickWeaponProfile arquebus = SupportedBoomsticks
                .profileFor("medieval_boomsticks:arquebus")
                .orElseThrow();
        BoomstickWeaponProfile arbalest = SupportedBoomsticks
                .profileFor("medieval_boomsticks:arbalest")
                .orElseThrow();

        assertEquals(8.0D, handgonne.projectileVelocity());
        assertEquals(1.0F, handgonne.inaccuracy());
        assertEquals(3, spikedHandgonne.projectileCount());
        assertEquals(1.0F, spikedHandgonne.inaccuracy());
        assertEquals(1.0F, arquebus.inaccuracy());
        assertEquals(1.6D, arbalest.projectileVelocity());
        assertEquals(1.0F, arbalest.inaccuracy());
    }

    @Test
    void unknownWeaponDoesNotEnterCompatibilityCatalog() {
        assertTrue(SupportedBoomsticks.profileFor("medieval_boomsticks:unknown").isEmpty());
    }
}
