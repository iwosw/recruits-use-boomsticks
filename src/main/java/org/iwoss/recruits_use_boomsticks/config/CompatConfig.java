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

    static {
        BUILDER.push("damage");
    }

    public static final ForgeConfigSpec.DoubleValue PROJECTILE_DAMAGE_MULTIPLIER = BUILDER
            .comment(
                    "Global damage multiplier for supported projectiles fired by recruits.",
                    "This is multiplied by the integration-specific value below; 1.0 keeps native damage.")
            .defineInRange("projectileDamageMultiplier", 1.0D, 0.0D, 100.0D);

    public static final ForgeConfigSpec.DoubleValue MEDIEVAL_BOOMSTICKS_DAMAGE_MULTIPLIER = BUILDER
            .comment("Additional damage multiplier for recruit-fired Medieval Boomsticks projectiles.")
            .defineInRange("medievalBoomsticksDamageMultiplier", 1.0D, 0.0D, 100.0D);

    public static final ForgeConfigSpec.DoubleValue ARTILLERY_ADDON_DAMAGE_MULTIPLIER = BUILDER
            .comment("Additional damage multiplier for recruit-fired Artillery Addon projectiles.")
            .defineInRange("artilleryAddonDamageMultiplier", 1.0D, 0.0D, 100.0D);

    public static final ForgeConfigSpec.DoubleValue MINIMUM_PROJECTILE_DAMAGE = BUILDER
            .comment(
                    "Minimum raw damage of each supported recruit projectile after the multipliers.",
                    "Minecraft uses 2 damage points per heart. The default 10.0 is five hearts.",
                    "Set this to 0.0 when a modpack wants to preserve native damage instead.")
            .defineInRange("minimumProjectileDamage", 10.0D, 0.0D, 2048.0D);

    public static final ForgeConfigSpec.BooleanValue PROJECTILES_IGNORE_HURT_COOLDOWN = BUILDER
            .comment(
                    "Let every projectile in a recruit volley deal damage instead of losing later",
                    "hits to Minecraft's short post-hit invulnerability window.",
                    "Enabled by default so every physical ball in a volley deals its configured damage.")
            .define("projectilesIgnoreHurtCooldown", true);

    static {
        BUILDER.pop();
    }

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

    public static double integrationDamageMultiplier(RecruitWeaponIntegration integration) {
        return switch (integration) {
            case MEDIEVAL_BOOMSTICKS -> MEDIEVAL_BOOMSTICKS_DAMAGE_MULTIPLIER.get();
            case ARTILLERY_ADDON -> ARTILLERY_ADDON_DAMAGE_MULTIPLIER.get();
        };
    }

    private CompatConfig() {
    }
}
