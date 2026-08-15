package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoomstickDamagePolicyTest {
    @Test
    void minimumMakesTwentyHealthTargetATwoHitKill() {
        assertEquals(10.0D, BoomstickDamagePolicy.calculateDamage(3.0D, 10.0D, 1.0D, 1.0D));
    }

    @Test
    void globalAndIntegrationMultipliersComposeWithoutCappingHeavyWeapons() {
        assertEquals(24.0D, BoomstickDamagePolicy.calculateDamage(6.0D, 10.0D, 2.0D, 2.0D));
    }

    @Test
    void zeroFloorAndMultipliersCanDisableDirectProjectileDamage() {
        assertEquals(0.0D, BoomstickDamagePolicy.calculateDamage(6.0D, 0.0D, 0.0D, 1.0D));
    }

    @Test
    void invalidOrNonPositiveNativeDamageStaysHarmless() {
        assertEquals(0.0D, BoomstickDamagePolicy.calculateDamage(0.0D, 10.0D, 1.0D, 1.0D));
        assertEquals(0.0D, BoomstickDamagePolicy.calculateDamage(Double.NaN, 10.0D, 1.0D, 1.0D));
    }
}
