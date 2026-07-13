package org.iwoss.recruits_use_boomsticks.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common configuration owned by the compatibility layer. */
public final class CompatConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable the custom Medieval Boomsticks combat goal for Recruits crossbowmen.")
            .define("enabled", true);

    public static final ForgeConfigSpec.BooleanValue ALLOW_STRATEGIC_FIRE = BUILDER
            .comment("Allow Recruits strategic-fire block positions to use supported Boomsticks weapons.")
            .define("allowStrategicFire", true);

    public static final ForgeConfigSpec.BooleanValue SMOKE_PARTICLES = BUILDER
            .comment("Emit safe server-synchronized smoke particles after a recruit fires.")
            .define("smokeParticles", true);

    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Log compatibility state transitions and adapter diagnostics.")
            .define("debugLogging", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private CompatConfig() {
    }
}
