package org.iwoss.recruits_use_boomsticks.gametest;

import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickAmmoAccess;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.MedievalBoomsticksAdapter;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@GameTestHolder(RecruitsUseBoomsticks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoomstickCompatibilityGameTests {
    private BoomstickCompatibilityGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void loadedHandgonneFiresAfterItsOnlyAmmoWasConsumedByReload(GameTestHelper helper) {
        boolean previousAmmoRequirement = RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get();
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(true);
        try {
            CrossBowmanEntity recruit = spawnCrossbowman(helper);
            ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
            ItemStack ammo = stack(SupportedBoomsticks.ROUND_BALL_ID);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            recruit.getInventory().addItem(ammo);

            BoomstickWeaponProfile profile = SupportedBoomsticks.profileFor(weapon).orElseThrow();
            helper.assertTrue(BoomstickAmmoAccess.consumeAmmo(recruit, profile, true), "reload must consume one round ball");
            helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0, "reload must leave no reserve ammo");

            MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);
            BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksAdapter.INSTANCE.fire(
                    recruit,
                    weapon,
                    recruit.position().add(10.0D, 0.0D, 0.0D));

            helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                    "a loaded weapon must not require a second round ball at fire time");
            helper.assertTrue(result.projectilesSpawned() == 1, "handgonne must spawn one projectile");
            helper.succeed();
        } finally {
            RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(previousAmmoRequirement);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void loadedSpikedHandgonneFiresThreeProjectilesAfterReload(GameTestHelper helper) {
        boolean previousAmmoRequirement = RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get();
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(true);
        try {
            CrossBowmanEntity recruit = spawnCrossbowman(helper);
            ItemStack weapon = stack(SupportedBoomsticks.SPIKED_HANDGONNE_ID);
            ItemStack ammo = stack(SupportedBoomsticks.ROUND_BALL_ID);
            ammo.setCount(3);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            recruit.getInventory().addItem(ammo);

            BoomstickWeaponProfile profile = SupportedBoomsticks.profileFor(weapon).orElseThrow();
            helper.assertTrue(BoomstickAmmoAccess.consumeAmmo(recruit, profile, true), "reload must consume three round balls");
            MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);

            BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksAdapter.INSTANCE.fire(
                    recruit,
                    weapon,
                    recruit.position().add(10.0D, 0.0D, 0.0D));

            helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                    "loaded spiked handgonne must fire without three additional round balls");
            helper.assertTrue(result.projectilesSpawned() == 3, "spiked handgonne must spawn three projectiles");
            helper.succeed();
        } finally {
            RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(previousAmmoRequirement);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void friendlyFireProtectionOnlyChangesRecruitOwnedProjectiles(GameTestHelper helper) {
        CrossBowmanEntity shooter = spawnCrossbowman(helper, 1);
        CrossBowmanEntity ally = spawnCrossbowman(helper, 2);
        Player player = helper.makeMockPlayer();
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam("rub_test_allies");
        if (team == null) {
            team = scoreboard.addPlayerTeam("rub_test_allies");
        }
        scoreboard.addPlayerToTeam(shooter.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        helper.assertTrue(shooter.isAlliedTo(ally), "scoreboard teammates must be allies");
        helper.assertTrue(player.isAlliedTo(ally), "player and recruit teammates must be allies");

        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        AbstractArrow recruitProjectile = new RoundBallProjectile(helper.getLevel(), shooter, weapon);
        AbstractArrow playerProjectile = new RoundBallProjectile(helper.getLevel(), player, weapon);
        recruitProjectile.setOwner(shooter);
        playerProjectile.setOwner(player);

        helper.assertFalse(canHitEntity(recruitProjectile, ally),
                "recruit-owned Boomsticks projectiles must skip allied recruits");
        helper.assertTrue(canHitEntity(playerProjectile, ally),
                "the compatibility mod must not change player-owned projectile targeting");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static CrossBowmanEntity spawnCrossbowman(GameTestHelper helper) {
        return spawnCrossbowman(helper, 1);
    }

    @SuppressWarnings("unchecked")
    private static CrossBowmanEntity spawnCrossbowman(GameTestHelper helper, int x) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id("recruits:crossbowman"));
        if (type == null) {
            helper.fail("missing recruits:crossbowman entity type");
            throw new IllegalStateException("missing recruits:crossbowman entity type");
        }
        return helper.spawnWithNoFreeWill((EntityType<CrossBowmanEntity>) type, x, 2, 1);
    }

    private static ItemStack stack(String registryId) {
        Item item = ForgeRegistries.ITEMS.getValue(id(registryId));
        if (item == null || item == BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("minecraft:air"))) {
            throw new IllegalStateException("missing item " + registryId);
        }
        return new ItemStack(item);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid registry ID " + value);
        }
        return id;
    }

    private static boolean canHitEntity(AbstractArrow projectile, Entity target) {
        try {
            Method method = AbstractArrow.class.getDeclaredMethod("canHitEntity", Entity.class);
            method.setAccessible(true);
            return (boolean) method.invoke(projectile, target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("could not inspect projectile hit predicate", exception);
        }
    }
}
