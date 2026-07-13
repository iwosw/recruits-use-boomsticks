package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Registry identity and data-tag catalog for the supported Boomsticks items. */
public final class SupportedBoomsticks {
    public static final String HANDGONNE_ID = "medieval_boomsticks:handgonne";
    public static final String SPIKED_HANDGONNE_ID = "medieval_boomsticks:spikedhandgonne";
    public static final String ARQUEBUS_ID = "medieval_boomsticks:arquebus";
    public static final String ARBALEST_ID = "medieval_boomsticks:arbalest";

    public static final String ROUND_BALL_ID = "medieval_boomsticks:round_ball";
    public static final String HEAVY_BOLT_ID = "medieval_boomsticks:heavy_bolt";

    private static final Map<String, BoomstickWeaponProfile> PROFILES = createProfiles();

    private SupportedBoomsticks() {
    }

    /**
     * Minecraft tag constants are held in a lazy nested class so pure profile tests do not
     * bootstrap the vanilla registries merely by loading this catalog.
     */
    public static final class Tags {
        public static final TagKey<Item> BOOMSTICK_WEAPONS = itemTag("boomstick_weapons");
        public static final TagKey<Item> ROUND_BALL_WEAPONS = itemTag("round_ball_weapons");
        public static final TagKey<Item> HEAVY_BOLT_WEAPONS = itemTag("heavy_bolt_weapons");
        public static final TagKey<Item> ROUND_BALL_AMMO = itemTag("round_ball_ammo");
        public static final TagKey<Item> HEAVY_BOLT_AMMO = itemTag("heavy_bolt_ammo");

        private Tags() {
        }
    }

    /** Returns an immutable view of every registry ID with an explicit compatibility profile. */
    public static Set<String> supportedWeaponIds() {
        return PROFILES.keySet();
    }

    public static Optional<BoomstickWeaponProfile> profileFor(String registryId) {
        return Optional.ofNullable(PROFILES.get(registryId));
    }

    /**
     * Looks up a profile using the registered item identity. Description/localized names are
     * deliberately not used because they vary by language and are not stable API identifiers.
     */
    public static Optional<BoomstickWeaponProfile> profileFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return profileFor(registryId(stack));
    }

    public static boolean isSupportedWeapon(ItemStack stack) {
        return profileFor(stack).isPresent();
    }

    public static boolean isSupportedAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String registryId = registryId(stack);
        return ROUND_BALL_ID.equals(registryId)
                || HEAVY_BOLT_ID.equals(registryId)
                || stack.is(Tags.ROUND_BALL_AMMO)
                || stack.is(Tags.HEAVY_BOLT_AMMO);
    }

    public static boolean isRoundBallAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return ROUND_BALL_ID.equals(registryId(stack)) || stack.is(Tags.ROUND_BALL_AMMO);
    }

    public static boolean isHeavyBoltAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return HEAVY_BOLT_ID.equals(registryId(stack)) || stack.is(Tags.HEAVY_BOLT_AMMO);
    }

    public static boolean usesRoundBall(ItemStack weapon) {
        return profileFor(weapon)
                .map(profile -> profile.ammoType() == BoomstickAmmoType.ROUND_BALL)
                .orElse(false);
    }

    public static boolean usesHeavyBolt(ItemStack weapon) {
        return profileFor(weapon)
                .map(profile -> profile.ammoType() == BoomstickAmmoType.HEAVY_BOLT)
                .orElse(false);
    }

    private static String registryId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(
                Registries.ITEM,
                new ResourceLocation(RecruitsUseBoomsticks.MOD_ID, path));
    }

    private static Map<String, BoomstickWeaponProfile> createProfiles() {
        Map<String, BoomstickWeaponProfile> profiles = new LinkedHashMap<>();
        profiles.put(HANDGONNE_ID, new BoomstickWeaponProfile(
                HANDGONNE_ID,
                BoomstickAmmoType.ROUND_BALL,
                25,
                1,
                8.0D,
                0.0F,
                5,
                false,
                BoomstickSound.HANDGONNE_SHOOT,
                BoomstickParticle.SMOKE));
        profiles.put(SPIKED_HANDGONNE_ID, new BoomstickWeaponProfile(
                SPIKED_HANDGONNE_ID,
                BoomstickAmmoType.ROUND_BALL,
                120,
                3,
                8.0D,
                0.0F,
                5,
                false,
                BoomstickSound.HANDGONNE_SHOOT,
                BoomstickParticle.SMOKE));
        profiles.put(ARQUEBUS_ID, new BoomstickWeaponProfile(
                ARQUEBUS_ID,
                BoomstickAmmoType.ROUND_BALL,
                25,
                1,
                8.0D,
                0.0F,
                5,
                false,
                BoomstickSound.ARQUEBUS_SHOOT,
                BoomstickParticle.SMOKE));
        profiles.put(ARBALEST_ID, new BoomstickWeaponProfile(
                ARBALEST_ID,
                BoomstickAmmoType.HEAVY_BOLT,
                50,
                1,
                1.6D,
                0.0F,
                5,
                true,
                BoomstickSound.CROSSBOW_SHOOT,
                BoomstickParticle.SMOKE));
        return Collections.unmodifiableMap(profiles);
    }
}
