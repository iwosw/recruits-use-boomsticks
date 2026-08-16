package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Explicit Medieval Boomsticks throwing-weapon boundary; unrelated throwable families stay disabled.
 *
 * <p>Every number below is read from the native item's own throw branch. Two native shapes exist and
 * both are {@code Player}-only, which is why a recruit reproduces them rather than entering the item
 * use path: {@code ThrowingItem.use} throws the moment the item is used, and the trident-shaped
 * items throw from {@code releaseUsing} once the use has been held for ten ticks.</p>
 */
public final class SupportedMedievalThrowables {
    public static final String MOD_ID = "medieval_boomsticks";

    public static final String THROWING_KNIFE_ID = MOD_ID + ":iron_throwing_knife";
    public static final String THROWING_AXE_ID = MOD_ID + ":iron_throwing_axe";
    public static final String SMALL_ROCK_ID = MOD_ID + ":small_throwing_rock";
    public static final String LARGE_ROCK_ID = MOD_ID + ":large_throwing_rock";
    public static final String WAR_DART_ID = MOD_ID + ":war_dart";
    public static final String JAVELIN_ID = MOD_ID + ":javelin";

    public static final String THROWN_KNIFE_PROJECTILE_ID = MOD_ID + ":thrown_knife";
    public static final String THROWN_AXE_PROJECTILE_ID = MOD_ID + ":thrown_axe";
    public static final String THROWN_SMALL_ROCK_PROJECTILE_ID = MOD_ID + ":thrown_small_rock";
    public static final String THROWN_LARGE_ROCK_PROJECTILE_ID = MOD_ID + ":thrown_large_rock";
    public static final String THROWN_WAR_DART_PROJECTILE_ID = MOD_ID + ":thrown_wardart";
    public static final String THROWN_JAVELIN_PROJECTILE_ID = MOD_ID + ":thrown_javelin";

    private static final String ENTITY_PACKAGE = "com.TBK.medieval_boomsticks.server.entity.";
    public static final String THROWN_KNIFE_PROJECTILE_CLASS = ENTITY_PACKAGE + "ThrowableKnife";
    public static final String THROWN_AXE_PROJECTILE_CLASS = ENTITY_PACKAGE + "ThrowableAxe";
    public static final String THROWN_SMALL_ROCK_PROJECTILE_CLASS =
            ENTITY_PACKAGE + "ThrowableSmallRock";
    public static final String THROWN_LARGE_ROCK_PROJECTILE_CLASS =
            ENTITY_PACKAGE + "ThrowableLargeRock";
    public static final String THROWN_WAR_DART_PROJECTILE_CLASS = ENTITY_PACKAGE + "ThrowableWardart";
    public static final String THROWN_JAVELIN_PROJECTILE_CLASS = ENTITY_PACKAGE + "ThrownJavelin";

    /** Shared NPC cadence between throws; the native items have no cooldown of their own. */
    public static final int COOLDOWN_TICKS = 10;
    /** Every native throw passes {@code 1.0F} as its {@code shootFromRotation} inaccuracy. */
    public static final float INACCURACY = 1.0F;
    /** Shortest use a trident-shaped native branch accepts before it will throw at all. */
    public static final int TRIDENT_WIND_UP_TICKS = 10;

    /**
     * The native families behind the throw, which decide how the projectile is built and which
     * runtime setting supplies its speed and damage.
     */
    public enum Family {
        /** {@code ThrowingKnifeItem.use}, thrown the instant the item is used. */
        KNIFE,
        /** {@code ThrowingAxeItem.use}, thrown instantly and able to carry the native curse flag. */
        AXE,
        /** {@code ThrowingSmallRockItem.releaseUsing}. */
        SMALL_ROCK,
        /** {@code ThrowingLargeRockItem.releaseUsing}. */
        LARGE_ROCK,
        /** {@code WarDartItem.releaseUsing}, a trident-shaped item with its own durability. */
        WAR_DART,
        /** {@code JavelinItem.releaseUsing}, a trident-shaped item with its own durability. */
        JAVELIN
    }

    /**
     * One confirmed Medieval Boomsticks throwing weapon.
     *
     * <p>Speed and damage are configurable in Medieval Boomsticks itself for several of these
     * families, so the values here are the native defaults and the adapter prefers the live setting
     * whenever the installed configuration supplies a usable one.</p>
     *
     * @param defaultVelocity native {@code shootFromRotation} velocity with the stock configuration
     * @param defaultDamage   damage the native projectile's own {@code onHitEntity} deals with the
     *                        stock configuration; the projectile reads it at impact rather than from
     *                        {@code baseDamage}, so the adapter never writes it onto the entity
     * @param windUpTicks     shortest native hold before the throw is accepted, or {@code 0} for the
     *                        instantly thrown items
     */
    public record MedievalThrowable(
            String weaponId,
            String projectileId,
            String projectileClassName,
            Family family,
            double defaultVelocity,
            double defaultDamage,
            float inaccuracy,
            int windUpTicks,
            BoomstickSound throwSound
    ) {
        public MedievalThrowable {
            if (weaponId == null || weaponId.isBlank()) {
                throw new IllegalArgumentException("weaponId must not be blank");
            }
            if (projectileId == null || projectileId.isBlank()) {
                throw new IllegalArgumentException("projectileId must not be blank");
            }
            if (projectileClassName == null || projectileClassName.isBlank()) {
                throw new IllegalArgumentException("projectileClassName must not be blank");
            }
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(throwSound, "throwSound");
            if (!Double.isFinite(defaultVelocity) || defaultVelocity <= 0.0D) {
                throw new IllegalArgumentException("defaultVelocity must be finite and positive");
            }
            if (!Double.isFinite(defaultDamage) || defaultDamage <= 0.0D) {
                throw new IllegalArgumentException("defaultDamage must be finite and positive");
            }
            if (!Float.isFinite(inaccuracy) || inaccuracy < 0.0F) {
                throw new IllegalArgumentException("inaccuracy must be finite and non-negative");
            }
            if (windUpTicks < 0) {
                throw new IllegalArgumentException("windUpTicks must be non-negative");
            }
        }

        public BoomstickWeaponProfile profile() {
            return profile(defaultVelocity);
        }

        /**
         * Builds the profile around a live configured speed, falling back to the native default when
         * the installed configuration has not been loaded yet or was set to a speed nothing can be
         * thrown at.
         */
        public BoomstickWeaponProfile profile(double velocity) {
            return new BoomstickWeaponProfile(
                    weaponId,
                    BoomstickAmmoType.THROWN_WEAPON,
                    1,
                    usableOr(velocity, defaultVelocity),
                    inaccuracy,
                    throwSound);
        }

        /** Resolves a live configured value, keeping the native default when it is unusable. */
        public static double usableOr(double configured, double fallback) {
            return Double.isFinite(configured) && configured > 0.0D ? configured : fallback;
        }
    }

    private static final Map<String, MedievalThrowable> THROWABLES = createThrowables();

    private SupportedMedievalThrowables() {
    }

    public static Set<String> supportedWeaponIds() {
        return THROWABLES.keySet();
    }

    public static Map<String, MedievalThrowable> throwables() {
        return THROWABLES;
    }

    public static Optional<MedievalThrowable> throwableFor(String registryId) {
        return Optional.ofNullable(THROWABLES.get(registryId));
    }

    public static Optional<MedievalThrowable> throwableFor(ItemStack stack) {
        return throwableFor(registryId(stack));
    }

    public static Optional<BoomstickWeaponProfile> profileFor(String registryId) {
        return throwableFor(registryId).map(MedievalThrowable::profile);
    }

    public static Optional<BoomstickWeaponProfile> profileFor(ItemStack stack) {
        return throwableFor(stack).map(MedievalThrowable::profile);
    }

    private static String registryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static Map<String, MedievalThrowable> createThrowables() {
        Map<String, MedievalThrowable> throwables = new LinkedHashMap<>();
        // ThrowingKnifeItem.use, speed ThrowingItem.getSpeedForType(KNIFE), damage
        // ThrowableKnife.onHitEntity reading Config.thrownKnifeDamage.
        put(throwables, new MedievalThrowable(
                THROWING_KNIFE_ID,
                THROWN_KNIFE_PROJECTILE_ID,
                THROWN_KNIFE_PROJECTILE_CLASS,
                Family.KNIFE,
                2.0D,
                8.0D,
                INACCURACY,
                0,
                BoomstickSound.THROW_WEAPON));
        // ThrowingAxeItem.use, speed ThrowingItem.getSpeedForType(AXE), damage
        // ThrowableAxe.onHitEntity reading Config.thrownAxeDamage.
        put(throwables, new MedievalThrowable(
                THROWING_AXE_ID,
                THROWN_AXE_PROJECTILE_ID,
                THROWN_AXE_PROJECTILE_CLASS,
                Family.AXE,
                1.6D,
                8.0D,
                INACCURACY,
                0,
                BoomstickSound.THROW_WEAPON));
        // ThrowingSmallRockItem.releaseUsing, speed Config.smallRockSpeed, damage
        // ThrowableSmallRock.onHitEntity reading Config.smallRockDamage.
        put(throwables, new MedievalThrowable(
                SMALL_ROCK_ID,
                THROWN_SMALL_ROCK_PROJECTILE_ID,
                THROWN_SMALL_ROCK_PROJECTILE_CLASS,
                Family.SMALL_ROCK,
                2.5D,
                4.0D,
                INACCURACY,
                TRIDENT_WIND_UP_TICKS,
                BoomstickSound.TRIDENT_THROW));
        // ThrowingLargeRockItem.releaseUsing, speed Config.largeRockSpeed, damage
        // ThrowableLargeRock.onHitEntity reading Config.largeRockDamage.
        put(throwables, new MedievalThrowable(
                LARGE_ROCK_ID,
                THROWN_LARGE_ROCK_PROJECTILE_ID,
                THROWN_LARGE_ROCK_PROJECTILE_CLASS,
                Family.LARGE_ROCK,
                1.0D,
                18.0D,
                INACCURACY,
                TRIDENT_WIND_UP_TICKS,
                BoomstickSound.TRIDENT_THROW));
        // WarDartItem.releaseUsing throws at a fixed 1.0 without a riptide level. Its projectile
        // deliberately reads Config.javelinDamage; the separate wardart damage settings are not
        // wired to ThrowableWardart.onHitEntity in the supported artifacts.
        put(throwables, new MedievalThrowable(
                WAR_DART_ID,
                THROWN_WAR_DART_PROJECTILE_ID,
                THROWN_WAR_DART_PROJECTILE_CLASS,
                Family.WAR_DART,
                1.0D,
                8.0D,
                INACCURACY,
                TRIDENT_WIND_UP_TICKS,
                BoomstickSound.TRIDENT_THROW));
        // JavelinItem.releaseUsing, speed Config.javelinSpeed, damage ThrownJavelin.onHitEntity
        // reading Config.javelinDamage.
        put(throwables, new MedievalThrowable(
                JAVELIN_ID,
                THROWN_JAVELIN_PROJECTILE_ID,
                THROWN_JAVELIN_PROJECTILE_CLASS,
                Family.JAVELIN,
                2.5D,
                8.0D,
                INACCURACY,
                TRIDENT_WIND_UP_TICKS,
                BoomstickSound.TRIDENT_THROW));
        return Collections.unmodifiableMap(throwables);
    }

    private static void put(Map<String, MedievalThrowable> throwables, MedievalThrowable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        if (throwables.put(throwable.weaponId(), throwable) != null) {
            throw new IllegalStateException("duplicate throwable id " + throwable.weaponId());
        }
    }
}
