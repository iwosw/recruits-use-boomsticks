package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit Artillery throwing-weapon boundary; unrelated throwable families stay disabled. */
public final class SupportedArtilleryThrowables {
    public static final String EPIC_KNIGHTS_ADDON_ID = "magistuarmoryaddon";
    public static final String EPIC_KNIGHTS_FRANCISCA_ID =
            EPIC_KNIGHTS_ADDON_ID + ":steel_francisca_axe";

    public static final String FRANCISCA_ID = SupportedArtillery.MOD_ID + ":francisca";
    public static final String HURLBAT_ID = SupportedArtillery.MOD_ID + ":hurlbat";
    public static final String THROWING_CROSS_ID = SupportedArtillery.MOD_ID + ":throwing_cross";
    public static final String JAVELIN_ID = SupportedArtillery.MOD_ID + ":javelin";
    public static final String THROWABLE_COBBLESTONE_ID =
            SupportedArtillery.MOD_ID + ":throwable_cobblestone";

    public static final String FRANCISCA_PROJECTILE_ID = SupportedArtillery.MOD_ID + ":francisca_pro";
    public static final String HURLBAT_PROJECTILE_ID = SupportedArtillery.MOD_ID + ":hurlbatpro";
    public static final String THROWING_CROSS_PROJECTILE_ID =
            SupportedArtillery.MOD_ID + ":throwing_cross_pro";
    public static final String JAVELIN_PROJECTILE_ID = SupportedArtillery.MOD_ID + ":javelin_proectile";
    public static final String THROWABLE_COBBLESTONE_PROJECTILE_ID =
            SupportedArtillery.MOD_ID + ":throwcobble";

    private static final String ENTITY_PACKAGE = "net.mcreator.artilleryaddon.entity.";
    public static final String FRANCISCA_PROJECTILE_CLASS = ENTITY_PACKAGE + "FranciscaProEntity";
    public static final String HURLBAT_PROJECTILE_CLASS = ENTITY_PACKAGE + "HurlbatproEntity";
    public static final String THROWING_CROSS_PROJECTILE_CLASS =
            ENTITY_PACKAGE + "ThrowingCrossProEntity";
    public static final String JAVELIN_PROJECTILE_CLASS = ENTITY_PACKAGE + "JavelinProectileEntity";
    public static final String THROWABLE_COBBLESTONE_PROJECTILE_CLASS =
            ENTITY_PACKAGE + "ThrowcobbleEntity";

    /** Shared NPC cadence between throws; the native items have no cooldown of their own. */
    public static final int COOLDOWN_TICKS = 10;
    /** Every native throwable is built by {@code createArrowWeaponItemStack(entity, 1, 0)}. */
    public static final int KNOCKBACK = 1;

    /**
     * One confirmed Artillery throwing weapon.
     *
     * <p>Every field is read from the native {@code ...PlayerFinishesUsingItemProcedure} full-use
     * branch, which is the only path an NPC can take: the charge-based {@code releaseUsing} branch
     * is {@code ServerPlayer}-only. The native construction is identical for all of them —
     * {@code createArrowWeaponItemStack(entity, 1, 0)}, then
     * {@code initArrowProjectile(arrow, thrower, baseDamage, true, false, false, pickup)}, then
     * {@code shoot(look.x, look.y, look.z, velocity, inaccuracy)} from {@code eyeY - 0.1} — so only
     * the per-weapon numbers below differ.</p>
     *
     * @param impactRecoveryChance native block-impact recovery roll, or {@code 0.0} when the binary
     *                             has no recovery procedure for this projectile
     */
    public record ArtilleryThrowable(
            String weaponId,
            String projectileId,
            String projectileClassName,
            int useDurationTicks,
            double baseDamage,
            double velocity,
            float inaccuracy,
            double impactRecoveryChance
    ) {
        public ArtilleryThrowable {
            if (weaponId == null || weaponId.isBlank()) {
                throw new IllegalArgumentException("weaponId must not be blank");
            }
            if (projectileId == null || projectileId.isBlank()) {
                throw new IllegalArgumentException("projectileId must not be blank");
            }
            if (projectileClassName == null || projectileClassName.isBlank()) {
                throw new IllegalArgumentException("projectileClassName must not be blank");
            }
            if (useDurationTicks <= 0) {
                throw new IllegalArgumentException("useDurationTicks must be positive");
            }
            if (!Double.isFinite(baseDamage) || baseDamage <= 0.0D) {
                throw new IllegalArgumentException("baseDamage must be finite and positive");
            }
            if (!Double.isFinite(velocity) || velocity <= 0.0D) {
                throw new IllegalArgumentException("velocity must be finite and positive");
            }
            if (!Float.isFinite(inaccuracy) || inaccuracy < 0.0F) {
                throw new IllegalArgumentException("inaccuracy must be finite and non-negative");
            }
            if (!Double.isFinite(impactRecoveryChance)
                    || impactRecoveryChance < 0.0D
                    || impactRecoveryChance > 1.0D) {
                throw new IllegalArgumentException("impactRecoveryChance must be within [0, 1]");
            }
        }

        public BoomstickWeaponProfile profile() {
            return new BoomstickWeaponProfile(
                    weaponId,
                    BoomstickAmmoType.THROWN_WEAPON,
                    1,
                    velocity,
                    inaccuracy,
                    BoomstickSound.NONE);
        }
    }

    private static final Map<String, ArtilleryThrowable> THROWABLES = createThrowables();
    private static final Set<String> PROJECTILE_CLASS_NAMES = THROWABLES.values().stream()
            .map(ArtilleryThrowable::projectileClassName)
            .collect(Collectors.toUnmodifiableSet());

    private SupportedArtilleryThrowables() {
    }

    public static Set<String> supportedWeaponIds() {
        return THROWABLES.keySet();
    }

    public static Set<String> supportedProjectileClassNames() {
        return PROJECTILE_CLASS_NAMES;
    }

    public static Map<String, ArtilleryThrowable> throwables() {
        return THROWABLES;
    }

    public static Optional<ArtilleryThrowable> throwableFor(String registryId) {
        return Optional.ofNullable(THROWABLES.get(registryId));
    }

    public static Optional<ArtilleryThrowable> throwableFor(ItemStack stack) {
        return throwableFor(registryId(stack));
    }

    public static Optional<BoomstickWeaponProfile> profileFor(String registryId) {
        return throwableFor(registryId).map(ArtilleryThrowable::profile);
    }

    public static Optional<BoomstickWeaponProfile> profileFor(ItemStack stack) {
        return throwableFor(stack).map(ArtilleryThrowable::profile);
    }

    public static boolean isSupportedProjectileClassName(String className) {
        return className != null && PROJECTILE_CLASS_NAMES.contains(className);
    }

    private static String registryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static Map<String, ArtilleryThrowable> createThrowables() {
        Map<String, ArtilleryThrowable> throwables = new LinkedHashMap<>();
        // FranciscaPlayerFinishesUseingProcedure.
        put(throwables, new ArtilleryThrowable(
                FRANCISCA_ID,
                FRANCISCA_PROJECTILE_ID,
                FRANCISCA_PROJECTILE_CLASS,
                15,
                5.5D,
                1.5D,
                1.9F,
                0.7D));
        // HurlbatPlayerFinishesUsingItemProcedure.
        put(throwables, new ArtilleryThrowable(
                HURLBAT_ID,
                HURLBAT_PROJECTILE_ID,
                HURLBAT_PROJECTILE_CLASS,
                14,
                6.5D,
                1.2D,
                2.0F,
                0.4D));
        // ThrowingCrossPlayerFinshesUseingProcedure.
        put(throwables, new ArtilleryThrowable(
                THROWING_CROSS_ID,
                THROWING_CROSS_PROJECTILE_ID,
                THROWING_CROSS_PROJECTILE_CLASS,
                13,
                4.5D,
                1.4D,
                1.7F,
                0.4D));
        // JavelinPlayerFinishesUsingItemProcedure.
        put(throwables, new ArtilleryThrowable(
                JAVELIN_ID,
                JAVELIN_PROJECTILE_ID,
                JAVELIN_PROJECTILE_CLASS,
                40,
                2.0D,
                3.5D,
                2.2F,
                0.75D));
        // ThrowableCobblestonePlayerFinishesUsingItemProcedure; no native recovery procedure exists.
        put(throwables, new ArtilleryThrowable(
                THROWABLE_COBBLESTONE_ID,
                THROWABLE_COBBLESTONE_PROJECTILE_ID,
                THROWABLE_COBBLESTONE_PROJECTILE_CLASS,
                30,
                15.0D,
                0.75D,
                0.0F,
                0.0D));
        return Collections.unmodifiableMap(throwables);
    }

    private static void put(Map<String, ArtilleryThrowable> throwables, ArtilleryThrowable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        if (throwables.put(throwable.weaponId(), throwable) != null) {
            throw new IllegalStateException("duplicate throwable id " + throwable.weaponId());
        }
    }
}
