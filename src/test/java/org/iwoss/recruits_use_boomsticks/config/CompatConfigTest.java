package org.iwoss.recruits_use_boomsticks.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatConfigTest {
    @Test
    void defaultsMatchCompatibilityPlan() {
        assertTrue(CompatConfig.ENABLED.getDefault());
        assertTrue(CompatConfig.MEDIEVAL_BOOMSTICKS_ENABLED.getDefault());
        assertTrue(CompatConfig.ARTILLERY_ADDON_ENABLED.getDefault());
        assertTrue(CompatConfig.ALLOW_STRATEGIC_FIRE.getDefault());
        assertTrue(CompatConfig.SMOKE_PARTICLES.getDefault());
        assertEquals(1.0D, CompatConfig.PROJECTILE_DAMAGE_MULTIPLIER.getDefault());
        assertEquals(1.0D, CompatConfig.MEDIEVAL_BOOMSTICKS_DAMAGE_MULTIPLIER.getDefault());
        assertEquals(1.0D, CompatConfig.ARTILLERY_ADDON_DAMAGE_MULTIPLIER.getDefault());
        assertEquals(10.0D, CompatConfig.MINIMUM_PROJECTILE_DAMAGE.getDefault());
        assertTrue(CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.getDefault());
        assertFalse(CompatConfig.DEBUG_LOGGING.getDefault());
    }
}
