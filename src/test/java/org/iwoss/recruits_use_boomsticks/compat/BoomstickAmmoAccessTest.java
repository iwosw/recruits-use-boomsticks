package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoomstickAmmoAccessTest {
    @Test
    void requiredAmmoMatchesTheProfileVolleySize() {
        BoomstickWeaponProfile handgonne = SupportedBoomsticks
                .profileFor(SupportedBoomsticks.HANDGONNE_ID)
                .orElseThrow();
        BoomstickWeaponProfile spikedHandgonne = SupportedBoomsticks
                .profileFor(SupportedBoomsticks.SPIKED_HANDGONNE_ID)
                .orElseThrow();

        assertEquals(1, BoomstickAmmoAccess.requiredAmmoUnits(handgonne, true));
        assertEquals(3, BoomstickAmmoAccess.requiredAmmoUnits(spikedHandgonne, true));
    }

    @Test
    void unlimitedAmmoRequiresNoInventoryUnits() {
        BoomstickWeaponProfile arbalest = SupportedBoomsticks
                .profileFor(SupportedBoomsticks.ARBALEST_ID)
                .orElseThrow();

        assertEquals(0, BoomstickAmmoAccess.requiredAmmoUnits(arbalest, false));
    }
}
