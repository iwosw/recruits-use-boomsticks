package org.iwoss.recruits_use_boomsticks.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponIntegration;

/** Common configuration owned by the compatibility layer. */
public final class CompatConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable all custom ranged-weapon compatibility behavior for Recruits crossbowmen.")
            .define("enabled", true);

    public static final ForgeConfigSpec.BooleanValue MEDIEVAL_BOOMSTICKS_ENABLED = BUILDER
            .comment("Enable the Medieval Boomsticks compatibility integration.")
            .define("medievalBoomsticksEnabled", true);

    public static final ForgeConfigSpec.BooleanValue ARTILLERY_ADDON_ENABLED = BUILDER
            .comment("Enable the Epic Knights: Artilleries and Firearms compatibility integration.")
            .define("artilleryAddonEnabled", true);

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

    public static boolean isIntegrationEnabled(RecruitWeaponIntegration integration) {
        if (!ENABLED.get()) {
            return false;
        }
        return switch (integration) {
            case MEDIEVAL_BOOMSTICKS -> MEDIEVAL_BOOMSTICKS_ENABLED.get();
            case ARTILLERY_ADDON -> ARTILLERY_ADDON_ENABLED.get();
        };
    }

    private CompatConfig() {
    }
}
