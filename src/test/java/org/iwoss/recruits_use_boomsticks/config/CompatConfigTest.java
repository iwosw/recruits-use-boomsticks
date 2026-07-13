package org.iwoss.recruits_use_boomsticks.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatConfigTest {
    @Test
    void defaultsMatchCompatibilityPlan() {
        assertTrue(CompatConfig.ENABLED.getDefault());
        assertTrue(CompatConfig.ALLOW_STRATEGIC_FIRE.getDefault());
        assertTrue(CompatConfig.SMOKE_PARTICLES.getDefault());
        assertFalse(CompatConfig.DEBUG_LOGGING.getDefault());
    }
}
