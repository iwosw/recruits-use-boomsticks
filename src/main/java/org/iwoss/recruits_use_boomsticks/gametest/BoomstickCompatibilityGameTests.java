package org.iwoss.recruits_use_boomsticks.gametest;

import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.entities.ai.FleeTNT;
import com.talhanation.recruits.entities.ai.compat.RecruitRangedMusketAttackGoal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickAttackState;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickCombatPolicy;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickFireCoordinator;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickThrowingAimFacing;
import org.iwoss.recruits_use_boomsticks.command.BoomstickCarryOrder;
import org.iwoss.recruits_use_boomsticks.ai.RecruitBoomstickAttackGoal;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickAmmoAccess;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickProjectileAttribution;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickTransientStateRecovery;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryAddonAdapter;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryThrowableAdapter;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryNativeState;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadProtocol;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadStep;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.Artillery1162Profiles;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.compat.SupportedArtillery;
import org.iwoss.recruits_use_boomsticks.compat.SupportedArtilleryThrowables;
import org.iwoss.recruits_use_boomsticks.compat.MedievalBoomsticksAdapter;
import org.iwoss.recruits_use_boomsticks.compat.MedievalBoomsticksThrowableAdapter;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;
import org.iwoss.recruits_use_boomsticks.compat.SupportedMedievalThrowables;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.iwoss.recruits_use_boomsticks.event.BoomstickProjectileEvents;
import org.iwoss.recruits_use_boomsticks.inventory.RecruitInventorySafety;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(RecruitsUseBoomsticks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoomstickCompatibilityGameTests {
    /** Real server ticks a repeater needs for one reload plus at least one follow-up cadence cycle. */
    private static final long COMBAT_CADENCE_TICKS = 150L;
    /** Concrete members of the native `minecraft:powder_flask` and `artillery:ramrod` tags. */
    private static final String POWDER_FLASK_ID = SupportedArtillery.MOD_ID + ":horn_flask";
    private static final String RAMROD_ID = SupportedArtillery.MOD_ID + ":ramrod";
    private static final String ROUND_BALL_PROJECTILE_CLASS =
            "com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile";
    private static final String HEAVY_BOLT_PROJECTILE_CLASS =
            "com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile";
    private static final String THROWN_JAVELIN_CLASS =
            "com.TBK.medieval_boomsticks.server.entity.ThrownJavelin";
    private static final String MEDIEVAL_BOOMSTICKS_CONFIG_CLASS =
            "com.TBK.medieval_boomsticks.Config";

    private BoomstickCompatibilityGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void equippedBoomstickReloadsBeforeRecruitReceivesATarget(GameTestHelper helper) {
        boolean previousAmmoRequirement = RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get();
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(true);
        try {
            CrossBowmanEntity recruit = spawnCrossbowman(helper);
            ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
            ItemStack ammo = stack(SupportedBoomsticks.ROUND_BALL_ID);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            recruit.getInventory().addItem(ammo);
            recruit.setTarget(null);
            recruit.setShouldRanged(true);

            RecruitBoomstickAttackGoal goal = RecruitBoomstickAttackGoal.passiveReload(recruit);
            helper.assertTrue(goal.canUse(), "an unloaded equipped weapon must start reloading without a target");
            goal.start();
            for (int tick = 0; tick <= MedievalBoomsticksAdapter.INSTANCE.reloadTicks(weapon); tick++) {
                goal.tick();
            }

            helper.assertTrue(MedievalBoomsticksAdapter.INSTANCE.isLoaded(weapon),
                    "weapon must already be loaded before a combat target is assigned");
            helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                    "pre-combat reload must consume exactly one round ball");
            CrossBowmanEntity fallbackRecruit = spawnCrossbowman(helper, 2);
            ItemStack unusableBoomstick = stack(SupportedBoomsticks.ARQUEBUS_ID);
            fallbackRecruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:crossbow"));
            fallbackRecruit.getInventory().addItem(stack("minecraft:arrow"));
            fallbackRecruit.getInventory().addItem(unusableBoomstick);
            fallbackRecruit.setTarget(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1));
            fallbackRecruit.setShouldRanged(true);
            helper.assertFalse(
                    RecruitWeaponAdapters.production()
                            .isUsableEnabledWeapon(fallbackRecruit, unusableBoomstick),
                    "an empty boomstick without its ammunition must not suppress the vanilla crossbow");
            helper.assertFalse(new RecruitBoomstickAttackGoal(fallbackRecruit, 1.0D).canUse(),
                    "boomstick AI must yield while only the vanilla crossbow can shoot");
            helper.succeed();
        } finally {
            RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(previousAmmoRequirement);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void acquiringAttackerDoesNotInterruptPassiveReload(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedBoomsticks.ARBALEST_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedBoomsticks.HEAVY_BOLT_ID));
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "fixture must start a passive reload");
        reloadGoal.start();
        reloadGoal.tick();
        helper.assertTrue(reloadGoal.phase() == BoomstickAttackState.Phase.RELOAD,
                "fixture must enter reload before the recruit is attacked");
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(weapon),
                "the Arbalest must remain in its lowered empty pose while its native reload runs");

        recruit.setTarget(helper.spawn(EntityType.ZOMBIE, 3, 2, 1));
        helper.assertTrue(reloadGoal.canContinueToUse(),
                "acquiring an attacker must not cancel an in-progress reload");
        helper.assertFalse(new RecruitBoomstickAttackGoal(recruit, 1.0D).canUse(),
                "combat goal must wait for the in-progress reload");
        reloadGoal.tick();
        helper.assertTrue(reloadGoal.phase() == BoomstickAttackState.Phase.RELOAD,
                "reload progress must continue after the recruit is attacked");
        helper.assertTrue(weapon.getOrCreateTag().getBoolean("recharge"),
                "reload animation must remain active after the recruit is attacked");
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(weapon),
                "an interrupted Arbalest reload must not enter its raised charged pose early");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void mountedRecruitReloadsBoomstickAtHalfSpeed(GameTestHelper helper) {
        boolean previousAmmoRequirement = RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get();
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(true);
        try {
            CrossBowmanEntity recruit = spawnCrossbowman(helper);
            Entity mount = helper.spawn(EntityType.HORSE, 1, 2, 2);
            helper.assertTrue(recruit.startRiding(mount, true), "fixture must mount the recruit");

            ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            recruit.getInventory().addItem(stack(SupportedBoomsticks.ROUND_BALL_ID));
            recruit.setTarget(null);
            recruit.setShouldRanged(true);

            RecruitBoomstickAttackGoal goal = RecruitBoomstickAttackGoal.passiveReload(recruit);
            int baseReloadTicks = MedievalBoomsticksAdapter.INSTANCE.reloadTicks(weapon);
            helper.assertTrue(goal.canUse(), "mounted recruits must be able to reload like upstream musket users");
            goal.start();
            for (int tick = 0; tick <= baseReloadTicks; tick++) {
                goal.tick();
            }
            helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(weapon),
                    "mounted reload must not finish at the normal on-foot duration");
            for (int tick = 0; tick <= baseReloadTicks; tick++) {
                goal.tick();
            }
            helper.assertTrue(MedievalBoomsticksAdapter.INSTANCE.isLoaded(weapon),
                    "mounted reload must finish after twice the normal duration");
            helper.succeed();
        } finally {
            RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(previousAmmoRequirement);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void arbalestUsesVanillaCrossbowBallisticArc(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedBoomsticks.ARBALEST_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);

        BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.getEyePosition().add(20.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "fixture must fire the arbalest");

        AbstractArrow bolt = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> isNamedType(candidate, HEAVY_BOLT_PROJECTILE_CLASS))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("arbalest did not spawn a heavy bolt"));
        // The native projectile applies random inaccuracy after the ballistic lead. Assert the
        // stable contract (positive vertical velocity), not one particular random magnitude.
        helper.assertTrue(bolt.getDeltaMovement().y > 0.0D,
                "long-range arbalest shots must lead upward like vanilla crossbow mobs");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unloadedBoomstickWithoutAmmoDoesNotStartPassiveReload(GameTestHelper helper) {
        boolean previousAmmoRequirement = RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get();
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(false);
        try {
            CrossBowmanEntity recruit = spawnCrossbowman(helper);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, stack(SupportedBoomsticks.HANDGONNE_ID));
            recruit.setTarget(null);
            recruit.setShouldRanged(true);

            RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
            helper.assertFalse(reloadGoal.canUse(),
                    "Boomsticks must require physical ammunition even when vanilla ranged ammo is optional");
            helper.succeed();
        } finally {
            RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(previousAmmoRequirement);
        }
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
            ListTag chargedProjectiles = weapon.getOrCreateTag()
                    .getList("ChargedProjectiles", Tag.TAG_COMPOUND);
            helper.assertTrue(chargedProjectiles.size() == 1,
                    "a recruit-loaded weapon must contain one native charged projectile");
            BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksAdapter.INSTANCE.fire(
                    recruit,
                    weapon,
                    recruit.position().add(10.0D, 0.0D, 0.0D));

            helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                    "a loaded weapon must not require a second round ball at fire time");
            helper.assertTrue(result.projectilesSpawned() == 1, "handgonne must spawn one projectile");
            helper.assertTrue(weapon.getOrCreateTag()
                            .getList("ChargedProjectiles", Tag.TAG_COMPOUND)
                            .isEmpty(),
                    "recruit fire must clear native charged projectiles");
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
            int damageBefore = weapon.getDamageValue();
            helper.assertTrue(weapon.getOrCreateTag()
                            .getList("ChargedProjectiles", Tag.TAG_COMPOUND)
                            .size() == 3,
                    "a recruit-loaded spiked handgonne must contain three native charged projectiles");

            BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksAdapter.INSTANCE.fire(
                    recruit,
                    weapon,
                    recruit.position().add(10.0D, 0.0D, 0.0D));

            helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                    "loaded spiked handgonne must fire without three additional round balls");
            helper.assertTrue(result.projectilesSpawned() == 3, "spiked handgonne must spawn three projectiles");
            helper.assertTrue(weapon.getDamageValue() == damageBefore + 3,
                    "spiked handgonne must lose one durability for each projectile in its volley");
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
        shooter.setOwnerUUID(Optional.of(player.getUUID()));
        ally.setOwnerUUID(Optional.of(player.getUUID()));
        shooter.setIsOwned(true);
        ally.setIsOwned(true);
        helper.assertFalse(shooter.canAttack(ally), "recruits with the same owner must not attack each other");

        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        AbstractArrow recruitProjectile = medievalProjectile(
                ROUND_BALL_PROJECTILE_CLASS, helper.getLevel(), shooter, weapon);
        AbstractArrow playerProjectile = medievalProjectile(
                ROUND_BALL_PROJECTILE_CLASS, helper.getLevel(), player, weapon);
        recruitProjectile.setOwner(shooter);
        playerProjectile.setOwner(player);

        helper.assertFalse(canHitEntity(recruitProjectile, ally),
                "recruit-owned Boomsticks projectiles must skip allied recruits");
        helper.assertTrue(canHitEntity(playerProjectile, ally),
                "the compatibility mod must not change player-owned projectile targeting");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void artilleryRecruitProjectilesUseTheSameFriendlyFireBoundary(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity shooter = spawnCrossbowman(helper, 1);
        CrossBowmanEntity ally = spawnCrossbowman(helper, 2);
        Player player = helper.makeMockPlayer();
        shooter.setOwnerUUID(Optional.of(player.getUUID()));
        ally.setOwnerUUID(Optional.of(player.getUUID()));
        shooter.setIsOwned(true);
        ally.setIsOwned(true);

        EntityType<?> projectileType = ForgeRegistries.ENTITY_TYPES.getValue(
                id(SupportedArtillery.IRONBALL_PROJECTILE_ID));
        helper.assertTrue(projectileType != null, "Artillery must register the Ironball projectile type");
        Entity created = projectileType.create(helper.getLevel());
        helper.assertTrue(created instanceof AbstractArrow,
                "Artillery Ironball must remain an AbstractArrow projectile");
        AbstractArrow projectile = (AbstractArrow) created;
        projectile.setOwner(shooter);

        helper.assertFalse(canHitEntity(projectile, ally),
                "recruit-owned Artillery projectiles must skip allied recruits");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void projectileAttributionSurvivesADeletedRecruit(GameTestHelper helper) {
        double previousMinimum = CompatConfig.MINIMUM_PROJECTILE_DAMAGE.get();
        CompatConfig.MINIMUM_PROJECTILE_DAMAGE.set(10.0D);
        try {
            CrossBowmanEntity shooter = spawnCrossbowman(helper, 1);
            CrossBowmanEntity ally = spawnCrossbowman(helper, 2);
            Player player = helper.makeMockPlayer();
            shooter.setOwnerUUID(Optional.of(player.getUUID()));
            ally.setOwnerUUID(Optional.of(player.getUUID()));
            shooter.setIsOwned(true);
            ally.setIsOwned(true);

            ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
            AbstractArrow projectile = medievalProjectile(
                    ROUND_BALL_PROJECTILE_CLASS, helper.getLevel(), shooter, weapon);
            projectile.setOwner(shooter);
            BoomstickProjectileAttribution.mark(projectile, shooter);
            shooter.discard();

            helper.assertTrue(projectile.getOwner() == null,
                    "the test must remove the projectile's live recruit owner");
            helper.assertFalse(canHitEntity(projectile, ally),
                    "a projectile must still skip its recruit owner's allies after that owner is gone");

            LivingEntity enemy = helper.spawn(EntityType.SKELETON, 4, 2, 1);
            enemy.setHealth(20.0F);
            helper.assertTrue(enemy.hurt(
                            helper.getLevel().damageSources().arrow(projectile, shooter),
                            4.0F),
                    "the attributed projectile must still hurt an enemy");
            helper.assertTrue(Math.abs(enemy.getHealth() - 10.0F) < 1.0E-6F,
                    "the configured damage floor must survive owner deletion, got " + enemy.getHealth());
            helper.succeed();
        } finally {
            CompatConfig.MINIMUM_PROJECTILE_DAMAGE.set(previousMinimum);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void expiredRecruitProjectilesAreRemovedWithoutChangingPlayerProjectiles(GameTestHelper helper) {
        CrossBowmanEntity shooter = spawnCrossbowman(helper);
        Player player = helper.makeMockPlayer();
        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        AbstractArrow recruitProjectile = medievalProjectile(
                ROUND_BALL_PROJECTILE_CLASS, helper.getLevel(), shooter, weapon);
        AbstractArrow playerProjectile = medievalProjectile(
                ROUND_BALL_PROJECTILE_CLASS, helper.getLevel(), player, weapon);
        recruitProjectile.setOwner(shooter);
        playerProjectile.setOwner(player);
        recruitProjectile.tickCount = 200;
        playerProjectile.tickCount = 200;

        recruitProjectile.tick();
        playerProjectile.tick();

        helper.assertTrue(recruitProjectile.isRemoved(),
                "recruit-owned Boomsticks projectiles must be discarded after ten seconds");
        helper.assertFalse(playerProjectile.isRemoved(),
                "the compatibility mod must not discard player-owned projectiles");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void airborneHeavyBoltsExpire(GameTestHelper helper) {
        CrossBowmanEntity shooter = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedBoomsticks.ARBALEST_ID);
        AbstractArrow airborne = medievalProjectile(
                HEAVY_BOLT_PROJECTILE_CLASS, helper.getLevel(), shooter, weapon);
        airborne.setOwner(shooter);
        airborne.pickup = AbstractArrow.Pickup.ALLOWED;
        airborne.tickCount = 200;

        airborne.tick();

        helper.assertTrue(airborne.isRemoved(),
                "an airborne recruit heavy bolt must not bypass the compatibility TTL");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void configuredRecruitProjectilesKillAnUnarmouredTargetInTwoHits(GameTestHelper helper) {
        double previousMinimum = CompatConfig.MINIMUM_PROJECTILE_DAMAGE.get();
        double previousGlobalMultiplier = CompatConfig.PROJECTILE_DAMAGE_MULTIPLIER.get();
        double previousIntegrationMultiplier = CompatConfig.MEDIEVAL_BOOMSTICKS_DAMAGE_MULTIPLIER.get();
        boolean previousHurtCooldown = CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.get();
        CompatConfig.MINIMUM_PROJECTILE_DAMAGE.set(10.0D);
        CompatConfig.PROJECTILE_DAMAGE_MULTIPLIER.set(1.0D);
        CompatConfig.MEDIEVAL_BOOMSTICKS_DAMAGE_MULTIPLIER.set(1.0D);
            CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.set(true);
        try {
            CrossBowmanEntity shooter = spawnCrossbowman(helper);
            LivingEntity target = helper.spawn(EntityType.SKELETON, 3, 2, 1);
            target.setHealth(20.0F);
            ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);

            AbstractArrow first = medievalProjectile(
                    ROUND_BALL_PROJECTILE_CLASS, helper.getLevel(), shooter, weapon);
            first.setOwner(shooter);
            target.invulnerableTime = 10;
            BoomstickProjectileEvents.onProjectileImpact(new ProjectileImpactEvent(
                    first,
                    new EntityHitResult(target)));
            helper.assertTrue(target.invulnerableTime == 0,
                    "a supported recruit projectile must open the target's volley hit window");
            helper.assertTrue(target.hurt(helper.getLevel().damageSources().arrow(first, shooter), 4.0F),
                    "the first configured projectile must hurt the target");
            helper.assertTrue(Math.abs(target.getHealth() - 10.0F) < 1.0E-6F,
                    "the configured floor must leave a 20-health target at exactly half health, got "
                            + target.getHealth());

            AbstractArrow second = medievalProjectile(
                    ROUND_BALL_PROJECTILE_CLASS, helper.getLevel(), shooter, weapon);
            second.setOwner(shooter);
            BoomstickProjectileEvents.onProjectileImpact(new ProjectileImpactEvent(
                    second,
                    new EntityHitResult(target)));
            helper.assertTrue(target.hurt(helper.getLevel().damageSources().arrow(second, shooter), 4.0F),
                    "the second configured projectile must hurt through the previous hit cooldown");
            helper.assertFalse(target.isAlive(),
                    "two configured recruit projectiles must kill an unarmoured 20-health target");
            helper.succeed();
        } finally {
            CompatConfig.MINIMUM_PROJECTILE_DAMAGE.set(previousMinimum);
            CompatConfig.PROJECTILE_DAMAGE_MULTIPLIER.set(previousGlobalMultiplier);
            CompatConfig.MEDIEVAL_BOOMSTICKS_DAMAGE_MULTIPLIER.set(previousIntegrationMultiplier);
            CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.set(previousHurtCooldown);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void passiveReloadDoesNotOwnMove(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedBoomsticks.ARBALEST_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedBoomsticks.HEAVY_BOLT_ID));
        RecruitBoomstickAttackGoal combatGoal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        TrackingMoveGoal moveGoal = new TrackingMoveGoal();
        BlockingMoveGoal blockingMoveGoal = new BlockingMoveGoal();

        helper.assertTrue(reloadGoal.canUse(), "passive reload must be eligible without a target");
        helper.assertFalse(reloadGoal.getFlags().contains(Goal.Flag.MOVE),
                "passive reload must not block follow, formation, swimming, or movement orders");

        recruit.goalSelector.removeAllGoals(ignored -> true);
        recruit.goalSelector.setNewGoalRate(1);
        recruit.goalSelector.addGoal(0, combatGoal);
        recruit.goalSelector.addGoal(1, reloadGoal);
        recruit.goalSelector.addGoal(2, moveGoal);
        recruit.goalSelector.tick();
        helper.assertTrue(isRunning(recruit, reloadGoal), "passive reload goal must start in the real selector");
        helper.assertTrue(isRunning(recruit, moveGoal),
                "a competing MOVE goal must run alongside passive reload");

        recruit.goalSelector.addGoal(0, blockingMoveGoal);
        recruit.goalSelector.tick();
        helper.assertTrue(isRunning(recruit, blockingMoveGoal),
                "fixture must establish a non-interruptible MOVE owner");

        LivingEntity target = helper.spawn(EntityType.ZOMBIE, 5, 2, 1);
        recruit.setTarget(target);
        recruit.goalSelector.tick();
        helper.assertFalse(isRunning(recruit, combatGoal),
                "combat goal must not bypass selector checks for a non-interruptible MOVE owner");
        helper.assertTrue(isRunning(recruit, blockingMoveGoal),
                "non-interruptible MOVE owner must retain its selector lock");

        recruit.goalSelector.removeGoal(blockingMoveGoal);
        recruit.goalSelector.tick();
        helper.assertFalse(isRunning(recruit, combatGoal),
                "combat goal must wait for an in-progress reload to finish");
        helper.assertTrue(isRunning(recruit, reloadGoal),
                "acquiring a target must not restart passive reload progress");
        helper.assertTrue(isRunning(recruit, moveGoal),
                "passive reload must still leave the MOVE lock free while under attack");

        for (int tick = 0; tick < 30 && !MedievalBoomsticksAdapter.INSTANCE.isLoaded(weapon); tick++) {
            recruit.goalSelector.tick();
        }
        recruit.goalSelector.tick();
        helper.assertTrue(MedievalBoomsticksAdapter.INSTANCE.isLoaded(weapon),
                "passive reload must finish before combat takes over");
        helper.assertTrue(isRunning(recruit, combatGoal),
                "combat goal must acquire selector locks after reload completes");
        helper.assertFalse(isRunning(recruit, reloadGoal),
                "completed passive reload must hand control to combat");
        helper.assertFalse(isRunning(recruit, moveGoal),
                "combat transition must acquire the selector's MOVE lock");

        // A live target deliberately survives a transient Recruits target clear so a shot does not
        // make the crossbowman forget its enemy. End this fixture's fight by invalidating the target.
        target.discard();
        recruit.setTarget(null);
        recruit.goalSelector.tick();
        helper.assertFalse(isRunning(recruit, reloadGoal),
                "a loaded weapon must not restart passive reload after losing the target");
        helper.assertTrue(isRunning(recruit, moveGoal),
                "combat-to-passive transition must release the selector's MOVE lock");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void passiveOrderStopsCombatAndIsNotWrittenBackOver(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 1);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(),
                "fixture must be able to fight before the passive order is given");

        // Recruits clears the target itself as the order lands; the goal must not hand it back.
        recruit.setAggroState(BoomstickCombatPolicy.PASSIVE_AGGRO_STATE);
        helper.assertFalse(goal.canUse(), "a passive recruit must not open the combat goal");

        goal.start();
        for (int tick = 0; tick < 10; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getTarget() == null,
                "a passive recruit must not have a target restored, it holds " + recruit.getTarget());
        helper.assertFalse(weapon.getOrCreateTag().getBoolean("fire"), "a passive recruit must not fire");
        helper.assertTrue(goal.phase() == BoomstickAttackState.Phase.IDLE,
                "a passive recruit must not enter an aim or fire phase, it is in " + goal.phase());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void boomstickTakesPrecedenceOverInstalledMusketModWeapon(GameTestHelper helper) {
        if (!ModList.get().isLoaded("musketmod")) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack musket = stack("musketmod:musket");
        ItemStack boomstick = stack(SupportedBoomsticks.HANDGONNE_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, musket);
        RecruitInventorySafety.putInStorageOrDrop(recruit, boomstick);
        RecruitInventorySafety.putInStorageOrDrop(recruit, stack(SupportedBoomsticks.ROUND_BALL_ID));
        recruit.setTarget(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 1));
        recruit.setShouldRanged(true);

        RecruitRangedMusketAttackGoal musketGoal = new RecruitRangedMusketAttackGoal(recruit, 1.0D);
        RecruitBoomstickAttackGoal boomstickGoal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertFalse(musketGoal.canUse(),
                "Recruits' Musket Mod goal must yield while a supported boomstick is available");
        helper.assertTrue(boomstickGoal.canUse(),
                "the boomstick goal must claim a supported weapon from storage");
        recruit.goalSelector.removeAllGoals(ignored -> true);
        recruit.goalSelector.setNewGoalRate(1);
        recruit.goalSelector.addGoal(0, musketGoal);
        recruit.goalSelector.addGoal(0, boomstickGoal);
        for (int tick = 0; tick < 5; tick++) {
            recruit.goalSelector.tick();
        }
        helper.assertTrue(recruit.getMainHandItem().is(stack(SupportedBoomsticks.HANDGONNE_ID).getItem()),
                "the boomstick goal must take precedence over Recruits' optional Musket Mod goal, it holds "
                        + recruit.getMainHandItem());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "emergencyMovement", timeoutTicks = 40)
    public static void combatYieldsToTntEmergencyMovement(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);
        recruit.setTarget(helper.spawn(EntityType.ZOMBIE, 5, 2, 1));

        RecruitBoomstickAttackGoal combatGoal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        FleeTNT fleeGoal = new FleeTNT(recruit);
        PrimedTnt tnt = helper.spawn(EntityType.TNT, 3, 2, 1);
        tnt.setFuse(80);

        recruit.goalSelector.removeAllGoals(ignored -> true);
        recruit.goalSelector.setNewGoalRate(1);
        recruit.goalSelector.addGoal(0, combatGoal);
        recruit.goalSelector.addGoal(1, fleeGoal);
        recruit.goalSelector.tick();

        helper.assertTrue(isRunning(recruit, fleeGoal),
                "the upstream TNT emergency goal must be running");
        helper.assertFalse(isRunning(recruit, combatGoal),
                "boomstick combat must yield MOVE and navigation while a primed TNT is nearby");
        tnt.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void swappingWeaponClearsFireFlagOnPreviousStack(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack previousWeapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(previousWeapon, true);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, previousWeapon);
        recruit.setTarget(helper.spawn(EntityType.ZOMBIE, 5, 2, 1));
        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        goal.start();
        for (int tick = 0; tick < 20 && !previousWeapon.getOrCreateTag().getBoolean("fire"); tick++) {
            goal.tick();
        }
        helper.assertTrue(previousWeapon.getOrCreateTag().getBoolean("fire"),
                "fixture must reach the firing animation");

        recruit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        goal.tick();

        helper.assertFalse(previousWeapon.getOrCreateTag().getBoolean("fire"),
                "changing weapons during the firing animation must clear Fire on the old stack");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void activeCooldownReleasesCombatMoveGoal(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);
        recruit.setTarget(helper.spawn(EntityType.ZOMBIE, 5, 2, 1));
        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);

        helper.assertTrue(goal.canUse(), "combat goal must be eligible before cooldown starts");
        recruit.getPersistentData().putLong(
                "recruits_use_boomsticks:boomstick_cooldown_until",
                helper.getLevel().getGameTime() + 200L
        );
        helper.assertFalse(goal.canUse(), "active cooldown must keep the MOVE-owning combat goal stopped");
        recruit.getPersistentData().remove("recruits_use_boomsticks:boomstick_cooldown_until");

        goal.start();
        for (int tick = 0; tick < 40 && goal.phase() != BoomstickAttackState.Phase.FIRE; tick++) {
            goal.tick();
        }
        helper.assertTrue(goal.phase() == BoomstickAttackState.Phase.FIRE,
                "fixture must reach the committed firing phase");
        helper.assertTrue(goal.canContinueToUse(),
                "goal must remain active long enough to process FIRED into COOLDOWN");

        goal.tick();
        helper.assertTrue(goal.phase() == org.iwoss.recruits_use_boomsticks.ai.BoomstickAttackState.Phase.COOLDOWN,
                "the committed shot must enter the persistent cooldown state");
        helper.assertTrue(goal.canContinueToUse(),
                "short firing animation must remain active after the state reaches COOLDOWN");

        goal.tick();
        goal.tick();
        helper.assertFalse(goal.canContinueToUse(),
                "after the firing animation the MOVE-owning combat goal must release during cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void liveTargetSurvivesThePostShotFacingRestore(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawn(EntityType.ZOMBIE, 5, 2, 1);
        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);
        recruit.setTarget(target);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        goal.start();
        for (int tick = 0; tick < 40 && goal.phase() != BoomstickAttackState.Phase.FIRE; tick++) {
            goal.tick();
        }
        helper.assertTrue(goal.phase() == BoomstickAttackState.Phase.FIRE,
                "fixture must reach the committed firing phase");

        // Reproduce the scheduling gap seen in a real formation: the boomstick goal releases LOOK
        // and MOVE for cooldown and an ordinary Recruits goal clears the Mob target in between.
        recruit.setTarget(null);
        goal.tick();

        helper.assertTrue(recruit.getTarget() == target,
                "restoring formation facing must not make a live combat target disappear");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void firingPoseKeepsTrackingTheTargetsEyes(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawn(EntityType.ZOMBIE, 5, 2, 1);
        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(weapon, true);
        recruit.setTarget(target);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        goal.start();
        for (int tick = 0; tick < 40 && goal.phase() != BoomstickAttackState.Phase.FIRE; tick++) {
            goal.tick();
        }
        helper.assertTrue(goal.phase() == BoomstickAttackState.Phase.FIRE,
                "fixture must reach the committed firing phase");

        Vec3 look = target.getEyePosition(1.0F).subtract(recruit.getEyePosition(1.0F));
        float expectedPitch = (float) (-(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
        helper.assertTrue(Math.abs(Mth.wrapDegrees(recruit.getXRot() - expectedPitch)) < 2.0F,
                "the firing pose must stay on the target's eyes instead of snapping to the low ballistic point");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void onlyLegacyLoadedNbtIsNormalized(GameTestHelper helper) {
        ItemStack legacy = stack(SupportedBoomsticks.HANDGONNE_ID);
        CrossbowItem.setCharged(legacy, true);
        helper.assertTrue(MedievalBoomsticksAdapter.INSTANCE.isLoaded(legacy),
                "legacy Charged=true must be normalized into native payload");
        helper.assertTrue(legacy.getOrCreateTag().getList("ChargedProjectiles", Tag.TAG_COMPOUND).size() == 1,
                "legacy handgonne must receive one native charged projectile");

        ItemStack malformed = stack(SupportedBoomsticks.SPIKED_HANDGONNE_ID);
        CrossbowItem.setCharged(malformed, true);
        malformed.getOrCreateTag().putString("ChargedProjectiles", "invalid");
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(malformed),
                "an explicitly malformed charged payload must be rejected");
        helper.assertFalse(CrossbowItem.isCharged(malformed),
                "rejecting malformed payload must clear Charged");
        helper.assertFalse(malformed.getOrCreateTag().contains("ChargedProjectiles"),
                "rejecting malformed payload must remove it");

        ListTag wrongCount = new ListTag();
        wrongCount.add(stack(SupportedBoomsticks.ROUND_BALL_ID).save(new CompoundTag()));
        CrossbowItem.setCharged(malformed, true);
        malformed.getOrCreateTag().put("ChargedProjectiles", wrongCount);
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(malformed),
                "a charged payload with the wrong projectile count must be rejected");
        helper.assertFalse(CrossbowItem.isCharged(malformed),
                "wrong projectile count must not create free ammunition");

        ListTag wrongAmmo = new ListTag();
        for (int index = 0; index < 3; index++) {
            wrongAmmo.add(stack(SupportedBoomsticks.HEAVY_BOLT_ID).save(new CompoundTag()));
        }
        CrossbowItem.setCharged(malformed, true);
        malformed.getOrCreateTag().put("ChargedProjectiles", wrongAmmo);
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(malformed),
                "a charged payload containing the wrong ammo item must be rejected");
        helper.assertFalse(CrossbowItem.isCharged(malformed),
                "wrong projectile item must not create free ammunition");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeAndRecruitLoadedStateInteroperateAcrossSave(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack nativeLoaded = stack(SupportedBoomsticks.HANDGONNE_ID);
        ListTag nativePayload = new ListTag();
        nativePayload.add(stack(SupportedBoomsticks.ROUND_BALL_ID).save(new CompoundTag()));
        nativeLoaded.getOrCreateTag().put("ChargedProjectiles", nativePayload);
        CrossbowItem.setCharged(nativeLoaded, true);

        BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksAdapter.INSTANCE.fire(
                recruit,
                nativeLoaded,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "NPC fire must accept a valid native player-loaded payload");

        ItemStack recruitLoaded = stack(SupportedBoomsticks.ARBALEST_ID);
        MedievalBoomsticksAdapter.INSTANCE.setLoaded(recruitLoaded, true);
        ItemStack restored = ItemStack.of(recruitLoaded.save(new CompoundTag()));
        helper.assertTrue(MedievalBoomsticksAdapter.INSTANCE.isLoaded(restored),
                "recruit-loaded state must survive item/world serialization");
        helper.assertTrue(restored.getOrCreateTag().getList("ChargedProjectiles", Tag.TAG_COMPOUND).size() == 1,
                "serialized recruit-loaded weapon must retain native payload for player firing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void artilleryArquebusNativeLoadProtocolIsNpcSafe(GameTestHelper helper) {
        ItemStack weapon = new ItemStack(Items.FLINT);
        ArtilleryNativeState.markLoaded(
                weapon,
                SupportedArtillery.profileFor(SupportedArtillery.ARQUEBUS_ID).orElseThrow());
        helper.assertTrue(
                ArtilleryNativeState.isLoaded(
                        weapon,
                        SupportedArtillery.profileFor(SupportedArtillery.ARQUEBUS_ID).orElseThrow()),
                "Artillery loading must be represented by native item state without a Player");
        helper.assertTrue(
                weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Artillery stage must retain the native double NBT type");
        helper.assertFalse(
                weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "Arquebus loading must not invent the addon's optional loaded flag");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void artilleryAvailabilityTracksOptionalModBoundary(GameTestHelper helper) {
        helper.assertTrue(
                ArtilleryAddonAdapter.INSTANCE.isAvailable()
                        == ModList.get().isLoaded(SupportedArtillery.MOD_ID),
                "Artillery adapter must not claim availability when the optional mod is absent");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void artilleryKillSwitchDisablesOnlyTheEnabledCompatibilityPath(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        boolean previousGlobal = CompatConfig.ENABLED.get();
        boolean previousArtillery = CompatConfig.ARTILLERY_ADDON_ENABLED.get();
        try {
            ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
            ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
            CompatConfig.ENABLED.set(true);
            CompatConfig.ARTILLERY_ADDON_ENABLED.set(false);
            helper.assertTrue(
                    RecruitWeaponAdapters.production().isSupportedWeapon(weapon),
                    "the adapter must still recognize its item while its integration is disabled");
            helper.assertFalse(
                    RecruitWeaponAdapters.production().isSupportedEnabledWeapon(weapon),
                    "the disabled Artillery integration must not suppress the vanilla Recruits goal");
            helper.assertFalse(
                    RecruitWeaponAdapters.production().isSupportedEnabledAmmo(ammo),
                    "the disabled Artillery integration must not claim iron balls for pickup");

            CompatConfig.ARTILLERY_ADDON_ENABLED.set(true);
            CompatConfig.ENABLED.set(false);
            helper.assertFalse(
                    RecruitWeaponAdapters.production().isSupportedEnabledWeapon(weapon),
                    "the global compatibility switch must disable the Artillery path");
            helper.assertFalse(
                    RecruitWeaponAdapters.production().isSupportedEnabledAmmo(ammo),
                    "the global compatibility switch must disable Artillery ammo pickup");
            helper.succeed();
        } finally {
            CompatConfig.ENABLED.set(previousGlobal);
            CompatConfig.ARTILLERY_ADDON_ENABLED.set(previousArtillery);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void artilleryArquebusAndIronBallAreAcceptedByRecruitPickup(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        helper.assertTrue(
                recruit.wantsToPickUp(stack(SupportedArtillery.ARQUEBUS_ID)),
                "the crossbowman pickup hook must accept the supported Arquebus");
        helper.assertTrue(
                recruit.wantsToPickUp(stack(SupportedArtillery.IRON_BALL_ID)),
                "the crossbowman pickup hook must accept the Arquebus iron ball");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void artilleryFranciscaConsumesOnePhysicalAxeAndSpawnsItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedArtilleryThrowables.FRANCISCA_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void artilleryHurlbatConsumesOnePhysicalItemAndSpawnsItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedArtilleryThrowables.HURLBAT_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void artilleryThrowingCrossConsumesOnePhysicalItemAndSpawnsItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedArtilleryThrowables.THROWING_CROSS_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void artilleryJavelinConsumesOnePhysicalItemAndSpawnsItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedArtilleryThrowables.JAVELIN_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void artilleryThrowableCobblestoneConsumesOneItemAndSpawnsItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedArtilleryThrowables.THROWABLE_COBBLESTONE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryFranciscaRunsThroughTheRecruitCombatGoal(GameTestHelper helper) {
        assertThrowableRunsThroughTheRecruitCombatGoal(
                helper, SupportedArtilleryThrowables.FRANCISCA_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryHurlbatRunsThroughTheRecruitCombatGoal(GameTestHelper helper) {
        assertThrowableRunsThroughTheRecruitCombatGoal(
                helper, SupportedArtilleryThrowables.HURLBAT_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryThrowingCrossRunsThroughTheRecruitCombatGoal(GameTestHelper helper) {
        assertThrowableRunsThroughTheRecruitCombatGoal(
                helper, SupportedArtilleryThrowables.THROWING_CROSS_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryJavelinRunsThroughTheRecruitCombatGoal(GameTestHelper helper) {
        assertThrowableRunsThroughTheRecruitCombatGoal(
                helper, SupportedArtilleryThrowables.JAVELIN_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryThrowableCobblestoneRunsThroughTheRecruitCombatGoal(
            GameTestHelper helper
    ) {
        assertThrowableRunsThroughTheRecruitCombatGoal(
                helper, SupportedArtilleryThrowables.THROWABLE_COBBLESTONE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryThrowableWindsUpOnlyWhileAiming(GameTestHelper helper) {
        BoomstickFireCoordinator.clearShared();
        SupportedArtilleryThrowables.ArtilleryThrowable nativeThrowable =
                SupportedArtilleryThrowables.throwableFor(SupportedArtilleryThrowables.HURLBAT_ID)
                        .orElseThrow();
        if (!ArtilleryThrowableAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtilleryThrowables.HURLBAT_ID)
                || !artilleryEntityRegistered(nativeThrowable.projectileId())) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 1);
        target.setHealth(1.0F);
        ItemStack held = stack(SupportedArtilleryThrowables.HURLBAT_ID, 2);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, held);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);
        recruit.setYRot(0.0F);
        recruit.setYHeadRot(0.0F);
        recruit.setYBodyRot(0.0F);

        helper.assertFalse(ArtilleryThrowableAdapter.INSTANCE.isAiming(held),
                "an idle throwing weapon must not be held in its wind-up");

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Hurlbat");
        goal.start();
        goal.tick();
        helper.assertTrue(ArtilleryThrowableAdapter.INSTANCE.isAiming(held),
                "entering the aim window must raise the throwing weapon");
        float targetYaw = (float) (Mth.atan2(
                target.getZ() - recruit.getZ(),
                target.getX() - recruit.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        // BetterRecruitFormations may steer the recruit and its mount first. The late server-tick
        // correction restores only the recruit's authorized aim without changing formation travel.
        applyBetterFormationHeading(recruit, 90.0F);
        BoomstickThrowingAimFacing.applyAfterFormations();
        helper.assertTrue(Math.abs(Mth.wrapDegrees(recruit.yBodyRot - targetYaw)) < 0.1F,
                "a throwing recruit must still face its target after late formation steering");

        CrossBowmanEntity deniedRecruit = spawnCrossbowman(helper, 2);
        ItemStack deniedHeld = stack(SupportedArtilleryThrowables.HURLBAT_ID, 2);
        deniedRecruit.setItemSlot(EquipmentSlot.MAINHAND, deniedHeld);
        deniedRecruit.setTarget(target);
        deniedRecruit.setShouldRanged(true);
        RecruitBoomstickAttackGoal deniedGoal = new RecruitBoomstickAttackGoal(deniedRecruit, 1.0D);
        deniedGoal.start();
        deniedGoal.tick();
        helper.assertFalse(ArtilleryThrowableAdapter.INSTANCE.isAiming(deniedHeld),
                "a recruit denied a firing reservation must keep its throwing arm down");
        deniedGoal.stop();

        for (int tick = 1; tick <= nativeThrowable.useDurationTicks(); tick++) {
            goal.tick();
        }
        helper.assertTrue(recruit.getMainHandItem().getCount() == 1,
                "the wind-up test must actually complete one throw");
        helper.assertFalse(ArtilleryThrowableAdapter.INSTANCE.isAiming(held),
                "the throw must drop the wind-up instead of leaving the arm cocked");

        goal.tick();
        recruit.getPersistentData().remove(
                "recruits_use_boomsticks:boomstick_cooldown_until");
        goal.tick();
        helper.assertFalse(ArtilleryThrowableAdapter.INSTANCE.isAiming(held),
                "a covered target must not start another wind-up when cooldown ends");

        goal.stop();
        helper.assertFalse(ArtilleryThrowableAdapter.INSTANCE.isAiming(recruit.getMainHandItem()),
                "a goal that ends mid-aim must not leave a wind-up marker behind");
        BoomstickFireCoordinator.clearShared();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void artilleryThrowableReachMatchesItsOwnProjectileSpeed(GameTestHelper helper) {
        if (!ArtilleryThrowableAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtilleryThrowables.THROWABLE_COBBLESTONE_ID)
                || !artilleryItemRegistered(SupportedArtilleryThrowables.JAVELIN_ID)) {
            helper.succeed();
            return;
        }

        double sharedRange = 45.0D;
        double cobblestoneReach = ArtilleryThrowableAdapter.INSTANCE.effectiveRange(
                stack(SupportedArtilleryThrowables.THROWABLE_COBBLESTONE_ID), sharedRange);
        double javelinReach = ArtilleryThrowableAdapter.INSTANCE.effectiveRange(
                stack(SupportedArtilleryThrowables.JAVELIN_ID), sharedRange);

        helper.assertTrue(cobblestoneReach < sharedRange,
                "a thrown cobblestone must report a shorter reach than the shared combat range");
        helper.assertTrue(cobblestoneReach > 0.0D,
                "a thrown cobblestone must still report a usable reach");
        helper.assertTrue(javelinReach == sharedRange,
                "a javelin outruns the shared combat range and must keep it unchanged");
        helper.assertTrue(
                ArtilleryThrowableAdapter.INSTANCE.effectiveRange(
                        stack(SupportedArtillery.ARQUEBUS_ID), sharedRange) == sharedRange,
                "a stack this adapter does not own must keep the shared combat range");
        helper.succeed();
    }

    /**
     * Every Artillery throwing weapon walks the same native full-use branch, so one body covers
     * them all and each weapon only supplies its own confirmed launch numbers.
     */
    private static void assertThrowableSpendsOneItemForItsNativeProjectile(
            GameTestHelper helper,
            String weaponId
    ) {
        SupportedArtilleryThrowables.ArtilleryThrowable nativeThrowable =
                SupportedArtilleryThrowables.throwableFor(weaponId).orElseThrow();
        if (!ArtilleryThrowableAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(weaponId)
                || !artilleryEntityRegistered(nativeThrowable.projectileId())) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper, 1);
        CrossBowmanEntity ally = spawnCrossbowman(helper, 2);
        Player owner = helper.makeMockPlayer();
        recruit.setOwnerUUID(Optional.of(owner.getUUID()));
        ally.setOwnerUUID(Optional.of(owner.getUUID()));
        recruit.setIsOwned(true);
        ally.setIsOwned(true);

        ItemStack held = stack(weaponId, 2);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, held);

        helper.assertTrue(recruit.wantsToPickUp(stack(weaponId)),
                "the crossbowman pickup hook must accept " + weaponId);
        helper.assertTrue(RecruitWeaponAdapters.production().find(held).orElseThrow()
                        == ArtilleryThrowableAdapter.INSTANCE,
                "the throwing adapter must uniquely own the " + weaponId + " stack");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryThrowableAdapter.INSTANCE.fire(
                recruit,
                held,
                recruit.position().add(8.0D, 1.0D, 0.0D));

        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                weaponId + " must throw from the logical server without the player-only item path");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "one physical " + weaponId + " must create one native projectile");
        helper.assertTrue(recruit.getMainHandItem().getCount() == 1,
                "a successful throw must spend exactly one held " + weaponId);

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals(nativeThrowable.projectileClassName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the native " + weaponId + " projectile was not spawned"));
        helper.assertTrue(
                Math.abs(projectile.getBaseDamage() - nativeThrowable.baseDamage()) < 1.0E-6D,
                "the native full-use " + weaponId + " branch must preserve its base damage");
        helper.assertTrue(projectile.getKnockback() == SupportedArtilleryThrowables.KNOCKBACK,
                "the native " + weaponId + " projectile must preserve knockback one");
        helper.assertTrue(projectile.isSilent(),
                "the native " + weaponId + " projectile must remain silent");
        helper.assertFalse(projectile.isCritArrow(),
                "the native " + weaponId + " projectile must remain non-critical");
        helper.assertTrue(projectile.getPierceLevel() == 0,
                "the native " + weaponId + " projectile must not pierce");
        AbstractArrow.Pickup expectedPickup = expectedNativeThrowablePickup();
        helper.assertTrue(projectile.pickup == expectedPickup,
                "the installed Artillery artifact passes " + expectedPickup + " for every throwable");
        helper.assertTrue(
                Math.abs(projectile.getDeltaMovement().length() - nativeThrowable.velocity()) < 0.15D,
                "the " + weaponId + " launch speed must stay near its native velocity");
        helper.assertFalse(canHitEntity(projectile, ally),
                "a recruit-owned " + weaponId + " must pass through allied recruits");
        helper.succeed();
    }

    /**
     * Native pickup mode differs between Artillery artifacts, so the expectation is read from the
     * installed one rather than pinned to a single release. Artillery 1.11 passes
     * {@code DISALLOWED} for every throwable and recovers the item in its own {@code onHitBlock}
     * roll; 1.14 passes {@code ALLOWED} and leaves the embedded projectile collectible, and marks
     * that shape by declaring {@code isStuckInGround}.
     */
    private static AbstractArrow.Pickup expectedNativeThrowablePickup() {
        for (String className : SupportedArtilleryThrowables.supportedProjectileClassNames()) {
            try {
                Class<?> projectileClass = Class.forName(
                        className,
                        false,
                        BoomstickCompatibilityGameTests.class.getClassLoader());
                projectileClass.getDeclaredMethod("isStuckInGround");
                return AbstractArrow.Pickup.ALLOWED;
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                // This artifact does not register the throwable, or does not carry the 1.14 marker.
            }
        }
        return AbstractArrow.Pickup.DISALLOWED;
    }

    private static void assertThrowableRunsThroughTheRecruitCombatGoal(
            GameTestHelper helper,
            String weaponId
    ) {
        SupportedArtilleryThrowables.ArtilleryThrowable nativeThrowable =
                SupportedArtilleryThrowables.throwableFor(weaponId).orElseThrow();
        if (!ArtilleryThrowableAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(weaponId)
                || !artilleryEntityRegistered(nativeThrowable.projectileId())) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 1);
        ItemStack held = stack(weaponId, 3);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, held);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        helper.assertTrue(ArtilleryThrowableAdapter.INSTANCE.isLoaded(held),
                "each held " + weaponId + " must be ready without an invented loaded marker");
        helper.assertFalse(RecruitBoomstickAttackGoal.passiveReload(recruit).canUse(),
                "a physical throwing stack must never enter the firearm reload state");

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(),
                "the recruit combat goal must claim an equipped " + weaponId);
        helper.assertTrue(
                ArtilleryThrowableAdapter.INSTANCE.aimTicks(held)
                        == nativeThrowable.useDurationTicks(),
                "the throwing adapter must preserve the native use duration of " + weaponId);
        goal.start();
        for (int tick = 0; tick < nativeThrowable.useDurationTicks(); tick++) {
            goal.tick();
        }
        helper.assertTrue(recruit.getMainHandItem().getCount() == 3,
                "the combat goal must not throw before the full native aim window elapses");
        goal.tick();

        helper.assertTrue(recruit.getMainHandItem().getCount() == 2,
                "the combat goal must complete one throw and spend one physical " + weaponId);
        helper.assertTrue(helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals(nativeThrowable.projectileClassName())),
                "the combat goal must hand off to the native " + weaponId + " projectile boundary");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalThrowingKnifeSpendsOneItemForItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertMedievalThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedMedievalThrowables.THROWING_KNIFE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalThrowingAxeSpendsOneItemForItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertMedievalThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedMedievalThrowables.THROWING_AXE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalSmallThrowingRockSpendsOneItemForItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertMedievalThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedMedievalThrowables.SMALL_ROCK_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalLargeThrowingRockSpendsOneItemForItsNativeProjectile(
            GameTestHelper helper
    ) {
        assertMedievalThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedMedievalThrowables.LARGE_ROCK_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalWarDartSpendsOneItemForItsNativeProjectile(GameTestHelper helper) {
        assertMedievalThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedMedievalThrowables.WAR_DART_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalJavelinSpendsOneItemForItsNativeProjectile(GameTestHelper helper) {
        assertMedievalThrowableSpendsOneItemForItsNativeProjectile(
                helper, SupportedMedievalThrowables.JAVELIN_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void medievalThrowingKnifeRunsThroughTheRecruitCombatGoal(GameTestHelper helper) {
        assertMedievalThrowableRunsThroughTheRecruitCombatGoal(
                helper, SupportedMedievalThrowables.THROWING_KNIFE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void medievalJavelinRunsThroughTheRecruitCombatGoal(GameTestHelper helper) {
        assertMedievalThrowableRunsThroughTheRecruitCombatGoal(
                helper, SupportedMedievalThrowables.JAVELIN_ID);
    }

    /**
     * A durable throwing weapon spends its last durability point in the recruit's hand.
     *
     * <p>The native branch damages the held stack and then removes it, which on the final point
     * leaves the throw holding an emptied stack. A recruit breaks the weapon instead and takes no
     * shot, so nothing is launched carrying an item that no longer exists.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalDurableThrowableBreaksInsteadOfThrowingAnEmptiedStack(
            GameTestHelper helper
    ) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack javelin = stack(SupportedMedievalThrowables.JAVELIN_ID);
        javelin.setDamageValue(javelin.getMaxDamage() - 1);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, javelin);

        BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksThrowableAdapter.INSTANCE.fire(
                recruit,
                javelin,
                recruit.position().add(8.0D, 1.0D, 0.0D));

        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.INVALID_WEAPON,
                "a javelin whose last durability point is due must refuse the throw");
        helper.assertTrue(result.projectilesSpawned() == 0,
                "a refused throw must not spawn a projectile");
        helper.assertTrue(recruit.getMainHandItem().isEmpty(),
                "the spent javelin must break in the recruit's hand");
        helper.assertTrue(helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .noneMatch(projectile -> projectile.getOwner() == recruit),
                "a broken javelin must leave nothing in the air");
        helper.succeed();
    }

    /**
     * The launch a recruit produces must be the launch the native player branch produces.
     *
     * <p>Both paths end in {@code Projectile.shoot}, which derives the projectile's own rotation from
     * its motion, and every Medieval Boomsticks projectile renderer draws the model from exactly that
     * rotation. Comparing the two side by side is therefore the whole visible contract: a thrown
     * weapon that points the wrong way is a projectile whose rotation does not match its flight.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void medievalJavelinLaunchMatchesTheNativePlayerBranch(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        Vec3 target = recruit.position().add(8.0D, 1.0D, 0.0D);
        ItemStack held = stack(SupportedMedievalThrowables.JAVELIN_ID, 2);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, held);

        BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksThrowableAdapter.INSTANCE.fire(
                recruit, held, target);
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "the javelin parity check must actually throw");

        AbstractArrow thrown = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && isNamedType(candidate, THROWN_JAVELIN_CLASS))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the javelin projectile was not spawned"));

        // The reference is the native branch itself: aim the recruit at the same point and launch a
        // second javelin exactly the way JavelinItem.releaseUsing does, without inaccuracy.
        Vec3 look = target.subtract(recruit.getX(), recruit.getEyeY() - 0.1D, recruit.getZ());
        recruit.setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F);
        recruit.setXRot((float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
        AbstractArrow reference = medievalProjectile(
                THROWN_JAVELIN_CLASS,
                helper.getLevel(),
                recruit,
                stack(SupportedMedievalThrowables.JAVELIN_ID));
        reference.shootFromRotation(
                recruit,
                recruit.getXRot(),
                recruit.getYRot(),
                0.0F,
                medievalJavelinSpeed(),
                0.0F);

        // The javelin points along its motion, so the two must agree on the direction of flight.
        helper.assertTrue(
                thrown.getDeltaMovement().normalize()
                        .dot(reference.getDeltaMovement().normalize()) > 0.95D,
                "a recruit's javelin must fly the way the native player branch throws it");
        helper.assertTrue(Math.abs(Mth.wrapDegrees(thrown.getYRot() - reference.getYRot())) < 5.0F,
                "a recruit's javelin must carry the native yaw its renderer draws the model from");
        helper.assertTrue(Math.abs(Mth.wrapDegrees(thrown.getXRot() - reference.getXRot())) < 15.0F,
                "a recruit's javelin must carry the native pitch, give or take its aim arc");
        helper.assertTrue(thrown.getYRot() == thrown.yRotO && thrown.getXRot() == thrown.xRotO,
                "the launch rotation must be settled before the renderer can interpolate from it");
        reference.discard();
        helper.succeed();
    }

    /**
     * The javelin's wind-up must reach the native item-use state its own model swap reads.
     *
     * <p>Medieval Boomsticks selects the javelin's throwing model through a predicate that is only
     * true while the holder is using the stack, and that model is pitched a hundred and seventy
     * degrees away from the carried one. A raised arm without the use state therefore shows a
     * javelin pointing backwards.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void medievalJavelinWindUpEntersTheNativeItemUseState(GameTestHelper helper) {
        BoomstickFireCoordinator.clearShared();
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 1);
        ItemStack held = stack(SupportedMedievalThrowables.JAVELIN_ID, 2);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, held);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        int aimTicks = MedievalBoomsticksThrowableAdapter.INSTANCE.aimTicks(held);
        helper.assertTrue(
                MedievalBoomsticksThrowableAdapter.INSTANCE.windUpUsesNativeItemState(held),
                "the javelin must draw its wind-up from the native item-use state");
        helper.assertTrue(held.getUseDuration() > aimTicks,
                "the javelin's vanilla use must never complete inside the wind-up");
        helper.assertFalse(recruit.isUsingItem(),
                "an idle recruit must not be holding a use state");

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped javelin");
        goal.start();
        goal.tick();

        helper.assertTrue(recruit.isUsingItem(),
                "entering the aim window must put the recruit into the native use state");
        // This identity test is the predicate Medieval Boomsticks itself evaluates for the model.
        helper.assertTrue(recruit.getUseItem() == recruit.getMainHandItem(),
                "the use state must name the held javelin the aim model predicate looks for");

        for (int tick = 1; tick <= aimTicks; tick++) {
            goal.tick();
        }
        helper.assertTrue(recruit.getMainHandItem().getCount() == 1,
                "the wind-up test must actually complete one throw");
        helper.assertFalse(recruit.isUsingItem(),
                "the throw must release the use state instead of leaving the arm cocked");

        // The instantly thrown family has no native use at all and must never enter one.
        CrossBowmanEntity knifeRecruit = spawnCrossbowman(helper, 2);
        ItemStack knife = stack(SupportedMedievalThrowables.THROWING_KNIFE_ID, 2);
        knifeRecruit.setItemSlot(EquipmentSlot.MAINHAND, knife);
        knifeRecruit.setTarget(target);
        knifeRecruit.setShouldRanged(true);
        helper.assertFalse(
                MedievalBoomsticksThrowableAdapter.INSTANCE.windUpUsesNativeItemState(knife),
                "an instantly thrown knife declares no use duration to render a wind-up from");
        RecruitBoomstickAttackGoal knifeGoal = new RecruitBoomstickAttackGoal(knifeRecruit, 1.0D);
        knifeGoal.start();
        knifeGoal.tick();
        helper.assertFalse(knifeRecruit.isUsingItem(),
                "a knife wind-up must not open a vanilla use a native finish path could complete");
        knifeGoal.stop();

        goal.stop();
        helper.assertFalse(recruit.isUsingItem(),
                "a goal that ends must not leave the recruit stuck in a use state");
        BoomstickFireCoordinator.clearShared();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void medievalThrowableReachMatchesItsOwnProjectileSpeed(GameTestHelper helper) {
        double sharedRange = 45.0D;
        double largeRockReach = MedievalBoomsticksThrowableAdapter.INSTANCE.effectiveRange(
                stack(SupportedMedievalThrowables.LARGE_ROCK_ID), sharedRange);
        double javelinReach = MedievalBoomsticksThrowableAdapter.INSTANCE.effectiveRange(
                stack(SupportedMedievalThrowables.JAVELIN_ID), sharedRange);

        helper.assertTrue(largeRockReach > 0.0D && largeRockReach < sharedRange,
                "a large throwing rock must report a shorter reach than the shared combat range");
        helper.assertTrue(javelinReach > largeRockReach && javelinReach < sharedRange,
                "a javelin must outrange a large rock and still fall inside the shared range");
        helper.assertTrue(
                MedievalBoomsticksThrowableAdapter.INSTANCE.effectiveRange(
                        stack(SupportedBoomsticks.HANDGONNE_ID), sharedRange) == sharedRange,
                "a stack this adapter does not own must keep the shared combat range");
        helper.succeed();
    }

    /**
     * Every Medieval Boomsticks throwing weapon reproduces the same native shape — one physical item
     * for one native projectile launched at its own speed — so one body covers them all.
     */
    private static void assertMedievalThrowableSpendsOneItemForItsNativeProjectile(
            GameTestHelper helper,
            String weaponId
    ) {
        SupportedMedievalThrowables.MedievalThrowable nativeThrowable =
                SupportedMedievalThrowables.throwableFor(weaponId).orElseThrow();

        CrossBowmanEntity recruit = spawnCrossbowman(helper, 1);
        CrossBowmanEntity ally = spawnCrossbowman(helper, 2);
        Player owner = helper.makeMockPlayer();
        recruit.setOwnerUUID(Optional.of(owner.getUUID()));
        ally.setOwnerUUID(Optional.of(owner.getUUID()));
        recruit.setIsOwned(true);
        ally.setIsOwned(true);

        ItemStack held = stack(weaponId, 2);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, held);

        helper.assertTrue(recruit.wantsToPickUp(stack(weaponId)),
                "the crossbowman pickup hook must accept " + weaponId);
        helper.assertTrue(RecruitWeaponAdapters.production().find(held).orElseThrow()
                        == MedievalBoomsticksThrowableAdapter.INSTANCE,
                "the throwing adapter must uniquely own the " + weaponId + " stack");
        helper.assertTrue(MedievalBoomsticksThrowableAdapter.INSTANCE.isLoaded(held),
                "each held " + weaponId + " must be ready without an invented loaded marker");

        BoomstickWeaponAdapter.ShotResult result = MedievalBoomsticksThrowableAdapter.INSTANCE.fire(
                recruit,
                held,
                recruit.position().add(8.0D, 1.0D, 0.0D));

        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                weaponId + " must throw from the logical server without the player-only item path");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "one physical " + weaponId + " must create one native projectile");
        helper.assertTrue(recruit.getMainHandItem().getCount() == 1,
                "a successful throw must spend exactly one held " + weaponId);

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals(nativeThrowable.projectileClassName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the native " + weaponId + " projectile was not spawned"));
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.ALLOWED,
                "the native " + weaponId + " branch leaves a thrown weapon collectible");
        helper.assertTrue(
                Math.abs(projectile.getDeltaMovement().length()
                        - nativeThrowable.defaultVelocity()) < 0.15D,
                "the " + weaponId + " launch speed must stay near its native velocity");
        helper.assertFalse(canHitEntity(projectile, ally),
                "a recruit-owned " + weaponId + " must pass through allied recruits");

        // The item a player recovers is the copy the projectile carries, and a durable weapon pays
        // its native durability point on that copy rather than on the stack left in the hand.
        ItemStack carried = ItemStack.of(
                projectile.saveWithoutId(new CompoundTag()).getCompound("Trident"));
        helper.assertFalse(carried.isEmpty(),
                "the native " + weaponId + " projectile must carry the item it was thrown as");
        helper.assertTrue(carried.is(held.getItem()),
                "the carried " + weaponId + " must be the weapon that was thrown");
        int expectedDamageValue = held.isDamageableItem() ? 1 : 0;
        helper.assertTrue(carried.getDamageValue() == expectedDamageValue,
                "a thrown " + weaponId + " must carry exactly the native durability cost");
        helper.succeed();
    }

    private static void assertMedievalThrowableRunsThroughTheRecruitCombatGoal(
            GameTestHelper helper,
            String weaponId
    ) {
        SupportedMedievalThrowables.MedievalThrowable nativeThrowable =
                SupportedMedievalThrowables.throwableFor(weaponId).orElseThrow();
        BoomstickFireCoordinator.clearShared();

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 1);
        ItemStack held = stack(weaponId, 3);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, held);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        helper.assertFalse(RecruitBoomstickAttackGoal.passiveReload(recruit).canUse(),
                "a physical throwing stack must never enter the firearm reload state");

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(),
                "the recruit combat goal must claim an equipped " + weaponId);
        int aimTicks = MedievalBoomsticksThrowableAdapter.INSTANCE.aimTicks(held);
        helper.assertTrue(aimTicks >= nativeThrowable.windUpTicks(),
                "the aim window must outlast the native wind-up of " + weaponId);
        goal.start();
        for (int tick = 0; tick < aimTicks; tick++) {
            goal.tick();
        }
        helper.assertTrue(recruit.getMainHandItem().getCount() == 3,
                "the combat goal must not throw before the full aim window elapses");
        goal.tick();

        helper.assertTrue(recruit.getMainHandItem().getCount() == 2,
                "the combat goal must complete one throw and spend one physical " + weaponId);
        helper.assertTrue(helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals(nativeThrowable.projectileClassName())),
                "the combat goal must hand off to the native " + weaponId + " projectile boundary");
        goal.stop();
        BoomstickFireCoordinator.clearShared();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryArquebusSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        ItemStack flask = stack(POWDER_FLASK_ID);
        ItemStack ramrod = stack(RAMROD_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        recruit.getInventory().addItem(flask);
        recruit.getInventory().addItem(ramrod);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.reloadStepCount(weapon) == 3,
                "the Arquebus must use the confirmed three-step native loading chain");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.hasReloadComponents(recruit, weapon),
                "the recruit must be seen as carrying the flask, ball, and ramrod the chain needs");

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(),
                "an Arquebus with a ball, a flask, and a ramrod must start the native chain");
        reloadGoal.start();

        // The native order is powder (stage 0.0), ball (stage 1.0), then ramming (stage 2.0).
        boolean[] sawPowderStage = {false};
        boolean[] sawBallStage = {false};
        boolean[] sawToolInOffhand = {false};
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
            double stage = weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY);
            boolean powderSet = weapon.getOrCreateTag().getDouble(ArtilleryNativeState.POWDER_KEY) >= 1.0D;
            if (powderSet && stage == 0.0D) {
                sawPowderStage[0] = true;
            }
            if (stage == 1.0D) {
                sawBallStage[0] = true;
            }
            if (!recruit.getOffhandItem().isEmpty()) {
                sawToolInOffhand[0] = true;
            }
        }

        helper.assertTrue(sawPowderStage[0],
                "the chain must pass through the native powder stage before loading the ball");
        helper.assertTrue(sawBallStage[0],
                "the chain must pass through the native stage-one ball state before ramming");
        helper.assertTrue(sawToolInOffhand[0],
                "the recruit must visibly hold each loading tool while the chain runs");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the completed native chain must leave the Arquebus loaded");
        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "the borrowed off hand must be restored once the chain finishes");

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the ball step must consume exactly one iron ball");
        // A borrowed tool must come back exactly once: the off hand is an inventory slot, so a
        // careless return would duplicate it.
        helper.assertTrue(recruit.getInventory().countItem(flask.getItem()) == 1,
                "the powder flask is damaged by the native step, never consumed or duplicated");
        helper.assertTrue(recruit.getInventory().countItem(ramrod.getItem()) == 1,
                "the ramrod is damaged by the native step, never consumed or duplicated");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryArquebusSteppedReloadRefusesAMissingRamrod(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        recruit.getInventory().addItem(stack(POWDER_FLASK_ID));
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.hasReloadComponents(recruit, weapon),
                "a missing ramrod must leave the native chain unsatisfied");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, true),
                "a weapon that cannot complete its native chain must not report itself ready to load");

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertFalse(reloadGoal.canUse(),
                "the recruit must not start a chain it cannot finish");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "an unsatisfied chain must leave the weapon unloaded");
        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "a refused chain must not borrow the off hand");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryReloadIgnoresAnOffhandOnlyComponent(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        ItemStack flask = stack(POWDER_FLASK_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, flask);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        recruit.getInventory().addItem(stack(RAMROD_ID));
        for (int slot = 0; slot < recruit.getInventory().getContainerSize(); slot++) {
            if (RecruitInventorySafety.isStorageSlot(recruit, slot)
                    && recruit.getInventory().getItem(slot).isEmpty()) {
                recruit.getInventory().setItem(slot, stack("minecraft:cobblestone"));
            }
        }

        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.hasReloadComponents(recruit, weapon),
                "an off-hand component with no safe storage slot must not pass reload preflight");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, true),
                "the recruit must not start a transaction that cannot borrow its off hand");
        helper.assertTrue(recruit.getOffhandItem().is(flask.getItem()),
                "a refused reload must leave the off-hand component untouched");
        helper.assertTrue(recruit.getInventory().countItem(flask.getItem()) == 1,
                "a refused reload must neither drop nor duplicate the off-hand component");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryInterruptedReloadResumesAfterItsCommittedSteps(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        ItemStack flask = stack(POWDER_FLASK_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        recruit.getInventory().addItem(flask);
        recruit.getInventory().addItem(stack(RAMROD_ID));

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.applyReloadStep(recruit, weapon, 0),
                "the interrupted transaction must commit its powder step");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.applyReloadStep(recruit, weapon, 1),
                "the interrupted transaction must commit and spend its ball step");
        ArtilleryAddonAdapter.INSTANCE.endSteppedReload(recruit, weapon);
        int flaskDamage = findInInventory(recruit, flask).getDamageValue();

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.completedReloadSteps(weapon) == 2,
                "the native stage-one payload must identify two completed steps");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, true),
                "the remaining ram step must not demand a second iron ball");

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "the partial native transaction must be resumable");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the resumed transaction must finish the remaining native ram step");
        helper.assertTrue(findInInventory(recruit, flask).getDamageValue() == flaskDamage,
                "resuming after the ball step must not damage the powder flask twice");
        helper.assertTrue(recruit.getInventory().countItem(stack(SupportedArtillery.IRON_BALL_ID).getItem()) == 0,
                "resuming must not require or create another iron ball");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryNobleReloadKeepsItsIronBallBranch(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.NOBLE_HANDGONNE_ID)
                || !artilleryItemRegistered(POWDER_FLASK_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.NOBLE_HANDGONNE_ID);
        ItemStack arrow = stack(SupportedArtillery.VANILLA_ARROW_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        recruit.getInventory().addItem(arrow);
        giveNativeReloadTools(recruit);

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, true),
                "the Noble Handgonne must select its preferred iron-ball branch");
        ArtilleryAddonAdapter.INSTANCE.setReloading(weapon, true);
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.applyReloadStep(recruit, weapon, 0),
                "the selected iron-ball branch must load powder");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.applyReloadStep(recruit, weapon, 1),
                "the selected iron-ball branch must spend its only ball");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, true),
                "the branch must remain locked after its ball leaves the inventory");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.reloadStepCount(weapon) == 3,
                "the transaction must retain the three-step iron-ball chain");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.applyReloadStep(recruit, weapon, 2),
                "the locked iron-ball branch must finish its ramming step");
        ArtilleryAddonAdapter.INSTANCE.endSteppedReload(recruit, weapon);
        ArtilleryAddonAdapter.INSTANCE.setReloading(weapon, false);

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Noble Handgonne must finish with its native loaded iron-ball payload");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 0.0D,
                "the finished branch must keep native ammo zero instead of switching to Arrow ammo two");
        helper.assertTrue(recruit.getInventory().countItem(arrow.getItem()) == 1,
                "locking the iron-ball branch must leave the fallback arrow untouched");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryArquebusCompletesNpcReloadAndServerShot(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "an Arquebus with an iron ball must start NPC reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(
                ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Arquebus reload must commit the native staged loaded state");
        helper.assertTrue(
                recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Arquebus reload must consume exactly one iron ball");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(
                result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Arquebus must fire from the logical server without Player procedures");
        helper.assertTrue(result.projectilesSpawned() == 1, "Arquebus must spawn one native projectile");
        helper.assertFalse(
                ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "firing must clear the native loaded state");
        helper.assertTrue(
                weapon.getOrCreateTag().getInt(ArtilleryNativeState.STAGE_KEY)
                        == SupportedArtillery.profileFor(SupportedArtillery.ARQUEBUS_ID)
                        .orElseThrow()
                        .firedStage(),
                "firing must leave the confirmed native fired stage");

        AbstractArrow artilleryProjectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(projectile -> projectile.getOwner() == recruit
                        && projectile.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the native Ironball projectile was not spawned"));
        helper.assertTrue(
                artilleryProjectile.isSilent(),
                "the native Arquebus projectile must preserve its silent flag");
        helper.assertTrue(
                artilleryProjectile.getKnockback() == 1,
                "the native Arquebus projectile must preserve its knockback strength");
        helper.assertTrue(
                Math.abs(artilleryProjectile.getBaseDamage() - 3.75D) < 1.0E-6D,
                "the native Arquebus projectile must preserve its base damage");
        helper.assertTrue(
                artilleryProjectile.getPierceLevel() == 0,
                "the native Arquebus projectile must not pierce targets");
        helper.assertTrue(
                artilleryProjectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "the native Arquebus projectile must not be collectible");
        helper.assertFalse(
                artilleryProjectile.isCritArrow(),
                "the native Arquebus projectile must not be critical");
        helper.assertTrue(
                artilleryProjectile.getRemainingFireTicks() == 0,
                "the native Arquebus projectile must not set targets on fire");
        helper.assertTrue(
                Math.abs(artilleryProjectile.getX() - recruit.getX()) < 1.0E-6D
                        && Math.abs(artilleryProjectile.getZ() - recruit.getZ()) < 1.0E-6D,
                "the native Arquebus projectile must preserve the shooter X/Z launch origin");
        helper.assertTrue(
                Math.abs(artilleryProjectile.getY() - (recruit.getEyeY() - 0.1D)) < 1.0E-6D,
                "the native Arquebus projectile must preserve the eyeY-minus-0.1 launch origin");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryHandCannonCompletesThreeBallReloadAndNativeVolley(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HAND_CANNON_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.HAND_CANNON_ID);
        // HandcannonRightclickProcedure shrinks one ball and then spawns its whole native volley.
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Hand Cannon with one iron ball must start NPC reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Hand Cannon reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Hand Cannon reload must consume exactly the one ball its native chain spends");

        BoomstickWeaponAdapter.ShotResult result = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            result = ArtilleryAddonAdapter.INSTANCE.fire(
                    recruit,
                    weapon,
                    recruit.position().add(10.0D, 0.0D, 0.0D));
            if (result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED) {
                break;
            }
            helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.MISFIRED,
                    "Hand Cannon fire may only reject through its confirmed native misfire boundary");
            ArtilleryNativeState.markLoaded(
                    weapon,
                    SupportedArtillery.profileFor(SupportedArtillery.HAND_CANNON_ID).orElseThrow());
        }

        helper.assertTrue(result != null && result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "Hand Cannon must eventually take its native successful volley branch");
        helper.assertTrue(result.projectilesSpawned() == 3,
                "Hand Cannon must spawn exactly three native Ironball projectiles");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Hand Cannon firing must clear its native loaded state");
        helper.assertTrue(
                weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY)
                        == SupportedArtillery.profileFor(SupportedArtillery.HAND_CANNON_ID)
                        .orElseThrow()
                        .firedStage(),
                "Hand Cannon firing must leave its native fired stage");

        var projectiles = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(projectile -> projectile.getOwner() == recruit
                        && projectile.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .toList();
        helper.assertTrue(projectiles.size() == 3,
                "the world must contain all three native Hand Cannon projectiles");
        for (AbstractArrow projectile : projectiles) {
            helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 3.5D : 2.0D)) < 1.0E-6D,
                    "Hand Cannon projectile must preserve its native base damage");
            helper.assertTrue(projectile.isSilent(),
                    "Hand Cannon projectile must preserve its native silent flag");
            helper.assertTrue(projectile.getKnockback() == 1,
                    "Hand Cannon projectile must preserve native knockback");
            helper.assertTrue(projectile.getPierceLevel() == 0,
                    "Hand Cannon projectile must not pierce targets");
            helper.assertFalse(projectile.isCritArrow(),
                    "Hand Cannon projectile must remain non-critical");
            helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                    "Hand Cannon projectile must not be collectible");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void artilleryHandCannonRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HAND_CANNON_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        // GameTests share one level and run concurrently. Give the still-attackable target enough
        // health that neighboring projectile tests cannot kill it before this goal fires.
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0D);
        target.setHealth(1024.0F);
        ItemStack weapon = stack(SupportedArtillery.HAND_CANNON_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        ammo.setCount(30);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Hand Cannon");
        goal.start();
        // The shot cadence is measured against level game time, so the reload and the volley only
        // advance on real server ticks. A tight in-tick loop can never leave the first cooldown.
        // The volley is latched at its widest, because the three native projectiles leave the
        // sampled box as they fly and would no longer be countable at the end of the run.
        int[] widestVolley = {0};
        for (long tick = 0L; tick < COMBAT_CADENCE_TICKS; tick++) {
            helper.runAtTickTime(tick, () -> {
                // Hand Cannon has a confirmed native random misfire branch. Reset the recruit RNG
                // immediately before each manually driven goal tick so this combat-path test proves
                // the volley deterministically instead of failing when its only shot window rolls an
                // entirely valid misfire. Dedicated misfire coverage owns the random branch itself.
                recruit.getRandom().setSeed(0L);
                goal.tick();
                int live = (int) helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .filter(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                        .count();
                widestVolley[0] = Math.max(widestVolley[0], live);
            });
        }
        helper.runAtTickTime(COMBAT_CADENCE_TICKS, () -> {
            helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) < 30,
                    "the combat goal must consume the one iron ball each native Hand Cannon volley loads");
            helper.assertTrue(widestVolley[0] >= 3,
                    "the combat goal must spawn a native three-projectile Hand Cannon volley");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryDoubleBarrelGonneLoadsFirstIronballBarrelAndFires(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.DOUBLE_BARREL_GONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.DOUBLE_BARREL_GONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.DOUBLE_BARREL_GONNE_ID)),
                "the crossbowman pickup hook must accept the Double Barrel Gonne");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.IRON_BALL_ID)),
                "the Double Barrel first-barrel branch must accept physical iron balls");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Double Barrel Gonne with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        CompoundTag loaded = weapon.getOrCreateTag();
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Double Barrel reload must commit the first-barrel native loaded state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Double Barrel reload must consume exactly one iron ball");
        helper.assertTrue(loaded.getTagType(ArtilleryNativeState.BARREL_ONE_KEY) == Tag.TAG_DOUBLE,
                "the first-barrel marker must retain the native double NBT type");
        helper.assertTrue(loaded.getDouble(ArtilleryNativeState.BARREL_ONE_KEY) == 2.0D,
                "the first barrel must retain native iron-ball code two");
        helper.assertTrue(loaded.getDouble(ArtilleryNativeState.RAMMED_ONE_KEY) == 1.0D,
                "the first barrel must retain native rammed one state");
        helper.assertTrue(loaded.getDouble(ArtilleryNativeState.BARREL_TWO_KEY) == 0.0D
                        && loaded.getDouble(ArtilleryNativeState.RAMMED_TWO_KEY) == 0.0D,
                "the second barrel must remain outside the one-barrel NPC slice");
        helper.assertTrue(loaded.getTagType(ArtilleryNativeState.LOADED_KEY) == Tag.TAG_DOUBLE
                        && loaded.getDouble(ArtilleryNativeState.LOADED_KEY) == 1.0D,
                "the native loaded count must identify one loaded barrel");
        helper.assertFalse(loaded.contains(ArtilleryNativeState.POWDER_KEY)
                        || loaded.contains(ArtilleryNativeState.STAGE_KEY)
                        || loaded.contains(ArtilleryNativeState.AMMO_KEY),
                "the Double Barrel branch must not invent staged or generic ammo markers");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded first barrel must fire its confirmed native iron-ball branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "the Double Barrel first-barrel branch must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Double Barrel firing must clear the first-barrel loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.BARREL_ONE_KEY) == 0.0D
                        && weapon.getOrCreateTag().getDouble(ArtilleryNativeState.RAMMED_ONE_KEY) == 0.0D
                        && weapon.getOrCreateTag().getDouble(ArtilleryNativeState.LOADED_KEY) == 0.0D,
                "Double Barrel firing must clear the first-barrel native state");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Double Barrel native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 4D : 2.7D)) < 1.0E-6D,
                "Double Barrel projectile must preserve its confirmed base damage");
        helper.assertTrue(projectile.isSilent() == Artillery1162Profiles.isInstalled(),
                "Double Barrel projectile must preserve the native audible flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Double Barrel projectile must preserve native knockback");
        helper.assertTrue(projectile.getPierceLevel() == (Artillery1162Profiles.isInstalled() ? 0 : 1),
                "Double Barrel projectile must preserve native piercing");
        helper.assertTrue(projectile.isCritArrow() == !Artillery1162Profiles.isInstalled(),
                "Double Barrel projectile must preserve its native critical flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Double Barrel projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryDoubleBarrelGonneFirstBarrelRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.DOUBLE_BARREL_GONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.DOUBLE_BARREL_GONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        ammo.setCount(6);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Double Barrel Gonne");
        goal.start();
        for (int tick = 0; tick < 80; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) < 6,
                "the Double Barrel combat path must consume an iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Double Barrel combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the combat goal must spawn the first-barrel native Ironball projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryArquebusAlwaysRequiresItsIronBall(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        boolean previousAmmoRequirement = RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get();
        RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(false);
        try {
            CrossBowmanEntity recruit = spawnCrossbowman(helper);
            ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            // The loading tools are present from the start, so only the missing ball is under test.
            giveNativeReloadTools(recruit);
            recruit.setTarget(null);
            recruit.setShouldRanged(true);

            RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
            helper.assertFalse(
                    ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, false),
                    "the Artillery adapter must require an iron ball even when the host reports optional ammo");
            helper.assertFalse(
                    reloadGoal.canUse(),
                    "Arquebus must not reload without an iron ball even when Recruits arrow requirements are disabled");

            ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
            recruit.getInventory().addItem(ammo);
            helper.assertTrue(
                    ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, false),
                    "one iron ball must satisfy the Artillery adapter independently of host ammo settings");
            helper.assertTrue(reloadGoal.canUse(), "one iron ball must make the Arquebus reloadable");
            reloadGoal.start();
            for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
                reloadGoal.tick();
            }

            helper.assertTrue(
                    ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                    "Arquebus reload must commit after the exact required component is present");
            helper.assertTrue(
                    recruit.getInventory().countItem(ammo.getItem()) == 0,
                    "Arquebus reload must consume its iron ball independently of Recruits arrow settings");
            helper.succeed();
        } finally {
            RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.set(previousAmmoRequirement);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryArquebusRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Arquebus");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the combat goal must consume the iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the combat goal must fire after committing the loaded state");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the combat goal must spawn the native Ironball projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryMatchlockMusketReloadsAndFiresItsNativeProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_MUSKET_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.MATCHLOCK_MUSKET_ID)),
                "the crossbowman pickup hook must accept the Matchlock Musket");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Matchlock Musket with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Musket reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Matchlock Musket reload must consume exactly one iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Matchlock Musket stage must retain the native double NBT type");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Matchlock Musket must fire without invoking player procedures");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Matchlock Musket must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Musket firing must clear the loaded state");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Matchlock Musket native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 5.5D : 4.5D)) < 1.0E-6D,
                "Matchlock Musket must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Matchlock Musket must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Matchlock Musket must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Matchlock Musket projectile must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Matchlock Musket projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryMatchlockMusketRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_MUSKET_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Matchlock Musket");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Matchlock Musket combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Matchlock Musket combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Matchlock Musket combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryMatchlockCarbineReloadsAndFiresItsStandingNativeProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_CARBINE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.MATCHLOCK_CARBINE_ID)),
                "the crossbowman pickup hook must accept the Matchlock Carbine");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Matchlock Carbine with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Carbine reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Matchlock Carbine reload must consume exactly one iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Matchlock Carbine stage must retain the native double NBT type");
        reloadGoal.stop();

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Matchlock Carbine must fire without invoking player procedures");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Matchlock Carbine must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Carbine firing must clear the loaded state");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Matchlock Carbine native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 5D : 2.7D)) < 1.0E-6D,
                "Matchlock Carbine must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Matchlock Carbine must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Matchlock Carbine must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Matchlock Carbine must preserve the native non-critical projectile flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Matchlock Carbine projectile must not be collectible");

        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.completedReloadSteps(weapon) == 0,
                "the fired stage-three payload must not look like a completed reload");
        RecruitBoomstickAttackGoal secondReload = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(secondReload.canUse(),
                "a fired Matchlock Carbine with fresh supplies must start another reload");
        secondReload.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            secondReload.tick();
        }
        secondReload.stop();
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Matchlock Carbine must complete its second reload after firing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryMatchlockCarbineRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_CARBINE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Matchlock Carbine");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Matchlock Carbine combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Matchlock Carbine combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Matchlock Carbine combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryMatchlockCarbineForkRestReloadsAndFiresItsRestProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.MATCHLOCK_CARBINE_ID)
                || !artilleryItemRegistered(SupportedArtillery.FORK_REST_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity invalidOffhandTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_CARBINE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Matchlock Carbine with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Carbine fork-rest reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Matchlock Carbine fork-rest reload must consume exactly one iron ball");

        recruit.setItemSlot(EquipmentSlot.OFFHAND, stack("minecraft:stick"));
        BoomstickWeaponAdapter.ShotResult rejected = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                invalidOffhandTarget.position());
        helper.assertTrue(rejected.outcome() == BoomstickWeaponAdapter.ShotOutcome.INVALID_WEAPON,
                "a non-empty non-rest offhand must not enter the native Matchlock Carbine firing branch");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "rejecting an unsupported offhand must preserve the loaded state");

        recruit.setItemSlot(EquipmentSlot.OFFHAND, stack(SupportedArtillery.FORK_REST_ID));
        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                invalidOffhandTarget.position());
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Matchlock Carbine with fork rest must fire its native branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Matchlock Carbine fork-rest firing must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Carbine fork-rest firing must clear the loaded state");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Matchlock Carbine fork-rest projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 5D : 2.7D)) < 1.0E-6D,
                "Matchlock Carbine fork-rest must preserve its native projectile damage");
        helper.assertTrue(projectile.isSilent(),
                "Matchlock Carbine fork-rest must preserve the native silent projectile flag");
        helper.assertFalse(projectile.isCritArrow(),
                "Matchlock Carbine fork-rest must preserve the native non-critical projectile flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Matchlock Carbine fork-rest projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryMatchlockCarbineForkRestRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.MATCHLOCK_CARBINE_ID)
                || !artilleryItemRegistered(SupportedArtillery.FORK_REST_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_CARBINE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, stack(SupportedArtillery.FORK_REST_ID));
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim a Matchlock Carbine with fork rest");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Matchlock Carbine fork-rest combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Matchlock Carbine fork-rest combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Matchlock Carbine fork-rest combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryMatchlockPistolReloadsAndFiresItsNativeProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_PISTOL_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.MATCHLOCK_PISTOL_ID)),
                "the crossbowman pickup hook must accept the Matchlock Pistol");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Matchlock Pistol with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Pistol reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Matchlock Pistol reload must consume exactly one iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Matchlock Pistol stage must retain the native double NBT type");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Matchlock Pistol must fire without invoking player procedures");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Matchlock Pistol must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Matchlock Pistol firing must clear the loaded state");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Matchlock Pistol native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 4.5D : 1.9D)) < 1.0E-6D,
                "Matchlock Pistol must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Matchlock Pistol must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Matchlock Pistol must preserve native projectile knockback");
        helper.assertTrue(projectile.isCritArrow() == !Artillery1162Profiles.isInstalled(),
                "Matchlock Pistol must preserve the native critical projectile flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Matchlock Pistol projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryMatchlockPistolRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_PISTOL_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Matchlock Pistol");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Matchlock Pistol combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Matchlock Pistol combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Matchlock Pistol combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryToradarRifleReloadsAndFiresItsNativeProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.TORADAR_RIFLE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.TORADAR_RIFLE_ID)),
                "the crossbowman pickup hook must accept the Toradar Rifle");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Toradar Rifle with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Toradar Rifle reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Toradar Rifle reload must consume exactly one iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Toradar Rifle stage must retain the native double NBT type");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Toradar Rifle must fire without invoking player procedures");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Toradar Rifle must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Toradar Rifle firing must clear the loaded state");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Toradar Rifle native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 6D : 3.2D)) < 1.0E-6D,
                "Toradar Rifle must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Toradar Rifle must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Toradar Rifle must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Toradar Rifle projectile must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Toradar Rifle projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryToradarRifleRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.TORADAR_RIFLE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Toradar Rifle");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Toradar Rifle combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Toradar Rifle combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Toradar Rifle combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryMiniPistolaReloadsAndFiresItsNativeProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.MINI_PISTOLA_ID);
        ItemStack ammo = stack(SupportedArtillery.SMALL_IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.MINI_PISTOLA_ID)),
                "the crossbowman pickup hook must accept the Mini Pistola");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.SMALL_IRON_BALL_ID)),
                "the crossbowman pickup hook must accept the Mini Pistola small iron ball");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Mini Pistola with one small iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Mini Pistola reload must commit its native stage-one state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Mini Pistola reload must consume exactly one small iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Mini Pistola stage must retain the native double NBT type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 1.0D,
                "Mini Pistola reload must use native stage one");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "Mini Pistola reload must not invent the optional loaded flag");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Mini Pistola must fire without invoking player procedures");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Mini Pistola must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Mini Pistola firing must clear the loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "Mini Pistola firing must leave native stage three");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Mini Pistola native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 1.0D) < 1.0E-6D,
                "Mini Pistola must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Mini Pistola must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Mini Pistola must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Mini Pistola projectile must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Mini Pistola projectile must not be collectible");
        helper.assertTrue(projectile.getRemainingFireTicks() == 0,
                "Mini Pistola projectile must not set targets on fire");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryMiniPistolaRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MINI_PISTOLA_ID);
        ItemStack ammo = stack(SupportedArtillery.SMALL_IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Mini Pistola");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Mini Pistola combat path must consume its small iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Mini Pistola combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Mini Pistola combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryTillerGunReloadsAndFiresItsNativeProjectile(GameTestHelper helper) {
        // The server-safe 1.11 test artifact predates Tiller Gun; execute this contract when the 1.14 item exists.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.TILLER_GUN_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.TILLER_GUN_ID);
        ItemStack ammo = stack(SupportedArtillery.SMALL_IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.TILLER_GUN_ID)),
                "the crossbowman pickup hook must accept the Tiller Gun");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.SMALL_IRON_BALL_ID)),
                "the crossbowman pickup hook must accept the Tiller Gun small iron ball");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Tiller Gun with one small iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Tiller Gun reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Tiller Gun reload must consume exactly one small iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Tiller Gun stage must retain the native double NBT type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 2.0D,
                "Tiller Gun reload must use native stage two");
        // TillerGunRightclickedProcedure writes loaded=true on its ramming step and loaded=false as
        // it fires, unlike the matchlock family, which never writes the flag at all.
        helper.assertTrue(weapon.getOrCreateTag().getBoolean(ArtilleryNativeState.LOADED_KEY),
                "Tiller Gun reload must set the native loaded flag its ramming step writes");

        recruit.setShiftKeyDown(true);
        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Tiller Gun must fire through the native shift launch branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Tiller Gun must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Tiller Gun firing must clear the loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "Tiller Gun firing must leave native stage three");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Tiller Gun native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 3D : 2.0D)) < 1.0E-6D,
                "Tiller Gun must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Tiller Gun must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Tiller Gun must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Tiller Gun projectile must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Tiller Gun projectile must not be collectible");
        helper.assertTrue(projectile.getRemainingFireTicks() == 0,
                "Tiller Gun projectile must not set targets on fire");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryTillerGunRunsThroughTheCombatGoal(GameTestHelper helper) {
        // The server-safe 1.11 test artifact predates Tiller Gun; execute this contract when the 1.14 item exists.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.TILLER_GUN_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.TILLER_GUN_ID);
        ItemStack ammo = stack(SupportedArtillery.SMALL_IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Tiller Gun");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            // This is a successful-shot contract; native misfire bounds have separate coverage.
            recruit.getRandom().setSeed(0);
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Tiller Gun combat path must consume its small iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Tiller Gun combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Tiller Gun combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryHarquebusReloadsAndFiresItsStandingNativeProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HARQUEBUS_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.HARQUEBUS_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.HARQUEBUS_ID)),
                "the crossbowman pickup hook must accept the Harquebus");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.IRON_BALL_ID)),
                "the crossbowman pickup hook must accept the Harquebus iron ball");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Harquebus with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Harquebus reload must commit its standing native ready state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Harquebus reload must consume exactly one iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Harquebus stage must retain the native double NBT type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "Harquebus reload must use its native stage three ready state");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "Harquebus reload must not invent the optional loaded flag");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Harquebus must fire through its standing branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Harquebus must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Harquebus firing must clear the loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 0.0D,
                "Harquebus firing must leave native stage zero");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Harquebus native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 4.5D : 2.2D)) < 1.0E-6D,
                "Harquebus must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Harquebus must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Harquebus must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Harquebus projectile must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Harquebus projectile must not be collectible");
        helper.assertTrue(projectile.getRemainingFireTicks() == 0,
                "Harquebus projectile must not set targets on fire");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryHarquebusRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HARQUEBUS_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.HARQUEBUS_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Harquebus");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Harquebus combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Harquebus combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Harquebus combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryHarquebusForkRestReloadsAndFiresItsRestProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HARQUEBUS_ID)
                || !artilleryItemRegistered(SupportedArtillery.FORK_REST_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity invalidOffhandTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.HARQUEBUS_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Harquebus with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Harquebus fork-rest reload must commit its native staged state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Harquebus fork-rest reload must consume exactly one iron ball");

        recruit.setItemSlot(EquipmentSlot.OFFHAND, stack("minecraft:stick"));
        BoomstickWeaponAdapter.ShotResult rejected = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                invalidOffhandTarget.position());
        helper.assertTrue(rejected.outcome() == BoomstickWeaponAdapter.ShotOutcome.INVALID_WEAPON,
                "a non-empty non-rest offhand must not enter the Harquebus fork-rest branch");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "rejecting an unsupported offhand must preserve the Harquebus loaded state");

        recruit.setItemSlot(EquipmentSlot.OFFHAND, stack(SupportedArtillery.FORK_REST_ID));
        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                invalidOffhandTarget.position());
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Harquebus with fork rest must fire its native rest branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Harquebus fork-rest firing must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Harquebus fork-rest firing must clear the loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 0.0D,
                "Harquebus fork-rest firing must leave native stage zero");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Harquebus fork-rest projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 4.5D : 2.2D)) < 1.0E-6D,
                "Harquebus fork-rest must preserve its native projectile damage");
        helper.assertTrue(projectile.isSilent(),
                "Harquebus fork-rest must preserve the native silent projectile flag");
        helper.assertFalse(projectile.isCritArrow(),
                "Harquebus fork-rest must preserve the native non-critical projectile flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Harquebus fork-rest projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryHarquebusForkRestRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HARQUEBUS_ID)
                || !artilleryItemRegistered(SupportedArtillery.FORK_REST_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.HARQUEBUS_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, stack(SupportedArtillery.FORK_REST_ID));
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim a Harquebus with fork rest");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Harquebus fork-rest combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Harquebus fork-rest combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Harquebus fork-rest combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryHackbutReloadsAndFiresItsStandingLargeBallProjectile(GameTestHelper helper) {
        // The pinned server-safe 1.11 test artifact predates Hackbut; execute this contract when the 1.14 item exists.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HACKBUT_ID)
                || !artilleryItemRegistered(SupportedArtillery.LARGE_IRON_BALL_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.HACKBUT_ID);
        ItemStack ammo = stack(SupportedArtillery.LARGE_IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.HACKBUT_ID)),
                "the crossbowman pickup hook must accept the Hackbut");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.LARGE_IRON_BALL_ID)),
                "the crossbowman pickup hook must accept the Hackbut large iron ball");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Hackbut with one large iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Hackbut reload must commit its native stage-two loaded state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Hackbut reload must consume exactly one large iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Hackbut stage must retain the native double NBT type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 2.0D,
                "Hackbut reload must use native stage two");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "Hackbut reload must not invent the optional loaded flag");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Hackbut must fire through its ordinary standing branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Hackbut must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Hackbut firing must clear the loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "Hackbut firing must leave native stage three");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Hackbut native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 10D : 5.25D)) < 1.0E-6D,
                "Hackbut must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent() == Artillery1162Profiles.isInstalled(),
                "Hackbut must preserve the native audible projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Hackbut must preserve native projectile knockback");
        helper.assertTrue(projectile.getPierceLevel() == 1,
                "Hackbut must preserve native projectile piercing");
        helper.assertTrue(projectile.isCritArrow() == !Artillery1162Profiles.isInstalled(),
                "Hackbut projectile must preserve the native critical flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Hackbut projectile must not be collectible");
        helper.assertTrue(projectile.getRemainingFireTicks() == 0,
                "Hackbut projectile must not set targets on fire");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryHackbutRunsThroughTheCombatGoal(GameTestHelper helper) {
        // The pinned server-safe 1.11 test artifact predates Hackbut; execute this contract when the 1.14 item exists.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HACKBUT_ID)
                || !artilleryItemRegistered(SupportedArtillery.LARGE_IRON_BALL_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.HACKBUT_ID);
        ItemStack ammo = stack(SupportedArtillery.LARGE_IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Hackbut");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Hackbut combat path must consume its large iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Hackbut combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Hackbut combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryHandgonneReloadsAndFiresItsNativeProjectile(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.HANDGONNE_ID)),
                "the crossbowman pickup hook must accept the Handgonne");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Handgonne with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Handgonne reload must commit the native stage-two loaded state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Handgonne reload must consume exactly one iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getBoolean(ArtilleryNativeState.LOADED_KEY),
                "Handgonne reload must set the native loaded flag");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Handgonne stage must retain the native double NBT type");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Handgonne must fire without invoking player procedures");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Handgonne must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Handgonne firing must clear its native loaded flag");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Handgonne native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 4D : 1.85D)) < 1.0E-6D,
                "Handgonne must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Handgonne must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Handgonne must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Handgonne projectile must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Handgonne projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryHandgonneRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Handgonne");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Handgonne combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Handgonne combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Handgonne combat goal must spawn its native projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryBronzeHandgonneRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.BRONZE_HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.BRONZE_HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.BRONZE_HANDGONNE_ID)),
                "the crossbowman pickup hook must accept Bronze Handgonne outside Artillery's guns tag");
        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Bronze Handgonne");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            recruit.getRandom().setSeed(0L);
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Bronze Handgonne must consume one physical iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 0.0D,
                "Bronze Handgonne must preserve native ammo zero");
        helper.assertFalse(weapon.getOrCreateTag().getBoolean(ArtilleryNativeState.LOADED_KEY),
                "fired Bronze Handgonne must clear its native loaded flag");
        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Bronze Handgonne native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 3.8D : 1.85D)) < 1.0E-6D,
                "Bronze Handgonne must preserve its confirmed projectile damage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryNobleHandgonneArrowBranchReloadsAndFires(GameTestHelper helper) {
        // Noble Handgonne is absent from the pinned 1.11 server-safe artifact; run when the item exists.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.NOBLE_HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.NOBLE_HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.NOBLE_HANDGONNE_ID)),
                "the crossbowman pickup hook must accept the Noble Handgonne");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.VANILLA_ARROW_ID)),
                "the Noble Handgonne Arrow branch must accept physical arrows");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Noble Handgonne with one arrow must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Noble Handgonne reload must commit its native stage-two loaded state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Noble Handgonne reload must consume exactly one physical arrow");
        // The native Arrow branch jumps to stage 2.0 with ammo=2.0 and never reaches the ramming
        // step that writes `loaded`, so the recruit payload must not invent one either.
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "the Noble Handgonne Arrow branch must not invent a loaded flag");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.AMMO_KEY) == Tag.TAG_DOUBLE,
                "Noble Handgonne must preserve the native double ammo marker type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 2.0D,
                "Noble Handgonne must preserve the native Arrow ammo code");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Noble Handgonne must fire its confirmed Arrow branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Noble Handgonne Arrow branch must spawn one projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Noble Handgonne firing must clear the native loaded flag");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "Noble Handgonne firing must leave native stage three");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName().equals("net.minecraft.world.entity.projectile.Arrow"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Noble Handgonne Arrow projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 3.5D : 2.55D)) < 1.0E-6D,
                "Noble Handgonne must preserve its confirmed Arrow base damage");
        helper.assertFalse(projectile.isSilent(),
                "Noble Handgonne must preserve the native non-silent Arrow flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Noble Handgonne must preserve native Arrow knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Noble Handgonne Arrow must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Noble Handgonne Arrow must not be collectible");
        helper.assertTrue(projectile.getPersistentData()
                        .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY),
                "the compatibility marker must distinguish Noble Handgonne Arrows from ordinary arrows");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.supportsProjectile(projectile),
                "the Artillery adapter must claim only the marked Noble Handgonne Arrow instance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryNobleHandgonneArrowBranchRunsThroughTheCombatGoal(GameTestHelper helper) {
        // Noble Handgonne is absent from the pinned 1.11 server-safe artifact; run when the item exists.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.NOBLE_HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.NOBLE_HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Noble Handgonne");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Noble Handgonne combat path must consume its physical arrow during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Noble Handgonne combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.minecraft.world.entity.projectile.Arrow")
                                && projectile.getPersistentData()
                                .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY)),
                "the Noble Handgonne combat goal must spawn its marked vanilla Arrow projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryNobleHandgonneAcceptsWeaponSlotAndFiresIronBall(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.NOBLE_HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.NOBLE_HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);

        helper.assertTrue(recruit.canHoldItem(weapon),
                "the real recruit weapon-slot predicate must accept Noble Handgonne");
        helper.assertTrue(recruit.canEquipItem(weapon),
                "Noble Handgonne must be equippable through the recruit inventory path");
        recruit.equipItem(weapon);
        helper.assertTrue(recruit.getMainHandItem() == weapon,
                "the inventory path must place Noble Handgonne in the main hand");

        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(),
                "a Noble Handgonne with an iron ball must enter the combat goal");
        goal.start();
        for (int tick = 0; tick < 75; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Noble Handgonne ball branch must consume one physical iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 0.0D,
                "the Noble Handgonne ball branch must preserve native ammo zero");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "the fired Noble Handgonne ball branch must leave native stage three");
        helper.assertFalse(weapon.getOrCreateTag().getBoolean(ArtilleryNativeState.LOADED_KEY),
                "the fired Noble Handgonne ball branch must clear its native loaded flag");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Noble Handgonne ball branch must spawn its native Ironball projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryMarkmengonneArrowBranchReloadsAndFires(GameTestHelper helper) {
        // Gate the contract so older or alternate Artillery artifacts remain a valid no-op.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.MARKMENGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.MARKMENGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.MARKMENGONNE_ID)),
                "the crossbowman pickup hook must accept the Markmengonne");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.VANILLA_ARROW_ID)),
                "the Markmengonne Arrow branch must accept physical arrows");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Markmengonne with one arrow must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Markmengonne reload must commit its native stage-two state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Markmengonne reload must consume exactly one physical arrow");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Markmengonne stage must retain the native double NBT type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 2.0D,
                "Markmengonne reload must use native stage two");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.AMMO_KEY) == Tag.TAG_DOUBLE,
                "Markmengonne must preserve the native double ammo marker type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 2.0D,
                "Markmengonne must preserve the native Arrow ammo code");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "Markmengonne reload must not invent the optional loaded flag");

        recruit.setShiftKeyDown(true);
        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Markmengonne must fire through its native shift Arrow branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Markmengonne Arrow branch must spawn one projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Markmengonne firing must clear its loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "Markmengonne firing must leave native stage three");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 2.0D,
                "Markmengonne firing must preserve the native Arrow ammo marker");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName().equals("net.minecraft.world.entity.projectile.Arrow"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Markmengonne Arrow projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 1.5D) < 1.0E-6D,
                "Markmengonne must preserve its confirmed Arrow base damage");
        helper.assertFalse(projectile.isSilent(),
                "Markmengonne must preserve the native non-silent Arrow flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Markmengonne must preserve native Arrow knockback");
        helper.assertTrue(projectile.getPierceLevel() == 0,
                "Markmengonne Arrow must not pierce targets");
        helper.assertFalse(projectile.isCritArrow(),
                "Markmengonne Arrow must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Markmengonne Arrow must not be collectible");
        helper.assertTrue(projectile.getRemainingFireTicks() == 0,
                "Markmengonne Arrow must not set targets on fire");
        helper.assertTrue(projectile.getPersistentData()
                        .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY),
                "the compatibility marker must distinguish Markmengonne Arrows from ordinary arrows");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.supportsProjectile(projectile),
                "the Artillery adapter must claim only the marked Markmengonne Arrow instance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryMarkmengonneArrowBranchRunsThroughTheCombatGoal(GameTestHelper helper) {
        // Gate the contract so older or alternate Artillery artifacts remain a valid no-op.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.MARKMENGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MARKMENGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Markmengonne");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Markmengonne combat path must consume its physical arrow during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Markmengonne combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.minecraft.world.entity.projectile.Arrow")
                                && projectile.getPersistentData()
                                .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY)),
                "the Markmengonne combat goal must spawn its marked vanilla Arrow projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryMarkmengonneIronBallBranchRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.MARKMENGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.MARKMENGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(),
                "a Markmen's Handgonne with an iron ball must select its native ball branch");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            recruit.getRandom().setSeed(0L);
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Markmen's Handgonne ball branch must consume one physical iron ball");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 0.0D,
                "Markmen's Handgonne ball branch must preserve native ammo zero");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "Markmen's Handgonne ball branch must not invent a loaded flag");
        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Markmen's Handgonne native Ironball projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 4.1D : 2.9D)) < 1.0E-6D,
                "Markmen's Handgonne must preserve its confirmed iron-ball damage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryWindlassCrossbowReloadsAndFiresItsMarkedArrow(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.WINDLASS_CROSSBOW_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.WINDLASS_CROSSBOW_ID);
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.WINDLASS_CROSSBOW_ID)),
                "the crossbowman pickup hook must accept the Windlass Crossbow");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.VANILLA_ARROW_ID)),
                "the Windlass Crossbow must accept physical arrows");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Windlass Crossbow with one arrow must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Windlass Crossbow reload must commit native stage four");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Windlass Crossbow reload must consume exactly one physical arrow");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Windlass Crossbow stage must retain the native double NBT type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 4.0D,
                "Windlass Crossbow reload must use native stage four");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.POWDER_KEY),
                "Windlass Crossbow must not invent the addon's powder marker");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Windlass Crossbow must fire its confirmed Arrow branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Windlass Crossbow must spawn one Arrow projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Windlass Crossbow firing must clear its loaded state");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 0.0D,
                "Windlass Crossbow firing must leave native stage zero");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName().equals("net.minecraft.world.entity.projectile.Arrow"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Windlass Crossbow Arrow projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.6D) < 1.0E-6D,
                "Windlass Crossbow must preserve its confirmed Arrow base damage");
        helper.assertFalse(projectile.isSilent(),
                "Windlass Crossbow must preserve the native non-silent Arrow flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Windlass Crossbow must preserve native Arrow knockback");
        helper.assertTrue(projectile.getPierceLevel() == 0,
                "Windlass Crossbow Arrow must not pierce targets");
        helper.assertTrue(projectile.isCritArrow(),
                "Windlass Crossbow must preserve the native critical Arrow flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.ALLOWED,
                "Windlass Crossbow Arrow must preserve native pickup allowance");
        helper.assertTrue(projectile.getPersistentData()
                        .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY),
                "the compatibility marker must distinguish Windlass Arrows from ordinary arrows");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.supportsProjectile(projectile),
                "the Artillery adapter must claim only the marked Windlass Arrow instance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryWindlassCrossbowRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.WINDLASS_CROSSBOW_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.WINDLASS_CROSSBOW_ID);
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Windlass Crossbow");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Windlass Crossbow combat path must consume its physical arrow during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Windlass Crossbow combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName().equals("net.minecraft.world.entity.projectile.Arrow")
                                && projectile.getPersistentData()
                                .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY)),
                "the Windlass Crossbow combat goal must spawn its marked Arrow projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryChuKoNuArrowShotReloadsAndFires(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.CHU_KO_NU_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.CHU_KO_NU_ID);
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.CHU_KO_NU_ID)
                .orElseThrow();
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID, profile.ammoPerReload());
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.CHU_KO_NU_ID)),
                "the crossbowman pickup hook must accept Chu Ko Nu");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.VANILLA_ARROW_ID)),
                "Chu Ko Nu must accept physical arrows");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Chu Ko Nu with a full magazine must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Chu Ko Nu reload must commit the native ammo counter");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Chu Ko Nu reload must consume exactly one physical arrow per magazine round");
        helper.assertTrue(weapon.getOrCreateTag().getTagType(ArtilleryNativeState.AMMO_KEY) == Tag.TAG_DOUBLE,
                "Chu Ko Nu ammo state must retain the native double NBT type");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY)
                        == (double) profile.magazineSize(),
                "Chu Ko Nu reload must fill the native eight-round magazine counter");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.STAGE_KEY),
                "Chu Ko Nu reload must not invent a stage marker");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.POWDER_KEY),
                "Chu Ko Nu reload must not invent a powder marker");

        // The confirmed native counter walks 8.0 down to 0.0, one shot each.
        BoomstickWeaponAdapter.ShotResult result = null;
        for (int round = profile.magazineSize(); round > 0; round--) {
            result = ArtilleryAddonAdapter.INSTANCE.fire(
                    recruit,
                    weapon,
                    recruit.position().add(10.0D, 0.0D, 0.0D));
            helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                    "every loaded Chu Ko Nu round must fire its confirmed Arrow branch");
            helper.assertTrue(result.projectilesSpawned() == 1,
                    "Chu Ko Nu Arrow branch must spawn one projectile per round");
            helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == round - 1.0D,
                    "Chu Ko Nu firing must spend exactly one native round");
            helper.assertTrue(ArtilleryNativeState.remainingRounds(weapon, profile) == round - 1,
                    "the Chu Ko Nu magazine counter must match the native ammo state");
            helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon) == (round > 1),
                    "Chu Ko Nu must stay loaded until its last native round is spent");
            helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                    "Chu Ko Nu must not consume more arrows while walking its magazine down");
        }

        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Chu Ko Nu firing must clear its native ammo count on the last round");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 0.0D,
                "Chu Ko Nu firing must leave native ammo count zero");
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.STAGE_KEY),
                "Chu Ko Nu firing must not add a stage marker");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.fire(
                        recruit,
                        weapon,
                        recruit.position().add(10.0D, 0.0D, 0.0D))
                        .outcome() == BoomstickWeaponAdapter.ShotOutcome.NOT_LOADED,
                "an empty Chu Ko Nu magazine must refuse a fourth shot");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName().equals("net.minecraft.world.entity.projectile.Arrow"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Chu Ko Nu Arrow projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 1.6D) < 1.0E-6D,
                "Chu Ko Nu must preserve its confirmed Arrow base damage");
        helper.assertFalse(projectile.isSilent(),
                "Chu Ko Nu must preserve the native non-silent Arrow flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Chu Ko Nu must preserve native Arrow knockback");
        helper.assertTrue(projectile.getPierceLevel() == 0,
                "Chu Ko Nu Arrow must not pierce targets");
        helper.assertTrue(projectile.isCritArrow(),
                "Chu Ko Nu must preserve the native critical Arrow flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.ALLOWED,
                "Chu Ko Nu Arrow must preserve native pickup allowance");
        helper.assertTrue(projectile.getPersistentData()
                        .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY),
                "the compatibility marker must distinguish Chu Ko Nu Arrows from ordinary arrows");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.supportsProjectile(projectile),
                "the Artillery adapter must claim only the marked Chu Ko Nu Arrow instance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void artilleryChuKoNuArrowShotRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.CHU_KO_NU_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.CHU_KO_NU_ID);
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.CHU_KO_NU_ID)
                .orElseThrow();
        ItemStack ammo = stack(SupportedArtillery.VANILLA_ARROW_ID, profile.ammoPerReload());
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Chu Ko Nu");
        goal.start();
        // Rounds are counted off the native magazine counter rather than off live projectiles,
        // because a recruit may pick its own fired arrows back up before the run ends.
        int[] previousRounds = {0};
        int[] reloads = {0};
        int[] roundsFired = {0};
        boolean[] sawMarkedArrow = {false};
        // The inter-shot cooldown is measured against level game time, so the repeater cadence only
        // advances on real server ticks. A tight in-tick loop can never leave the first cooldown.
        for (long tick = 0L; tick < COMBAT_CADENCE_TICKS; tick++) {
            helper.runAtTickTime(tick, () -> {
                goal.tick();
                int rounds = ArtilleryNativeState.remainingRounds(weapon, profile);
                // The native chain counts the magazine up one arrow at a time, so only a refill that
                // starts from an empty counter is a new reload transaction.
                if (rounds > previousRounds[0]) {
                    if (previousRounds[0] == 0) {
                        reloads[0]++;
                    }
                } else if (rounds < previousRounds[0]) {
                    roundsFired[0] += previousRounds[0] - rounds;
                    // Latched on the firing tick; a picked-up arrow is gone by the end of the run.
                    sawMarkedArrow[0] |= helper.getLevel()
                            .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                            .stream()
                            .anyMatch(projectile -> projectile.getOwner() == recruit
                                    && projectile.getClass().getName()
                                    .equals("net.minecraft.world.entity.projectile.Arrow")
                                    && projectile.getPersistentData()
                                    .getBoolean(ArtilleryAddonAdapter.COMPATIBILITY_MARKER_KEY));
                }
                previousRounds[0] = rounds;
            });
        }
        helper.runAtTickTime(COMBAT_CADENCE_TICKS, () -> {
            helper.assertTrue(roundsFired[0] >= 2,
                    "the Chu Ko Nu combat goal must fire more than one round from a single magazine");
            helper.assertTrue(reloads[0] == 1,
                    "the Chu Ko Nu combat path must not reload between rounds of the same magazine");
            helper.assertTrue(roundsFired[0] + previousRounds[0] == profile.magazineSize(),
                    "spent and remaining Chu Ko Nu rounds must add up to one native magazine");
            helper.assertTrue(sawMarkedArrow[0],
                    "the Chu Ko Nu combat goal must spawn its marked Arrow projectile");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artilleryTaccolaIronBallBranchReloadsAndFires(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.TACCOLA_HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.TACCOLA_HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.TACCOLA_HANDGONNE_ID)),
                "the crossbowman pickup hook must accept the Taccola Handgonne");
        helper.assertTrue(recruit.wantsToPickUp(stack(SupportedArtillery.IRON_BALL_ID)),
                "the Taccola iron-ball branch must accept physical iron balls");
        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "a Taccola Handgonne with one iron ball must start reload");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        CompoundTag loaded = weapon.getOrCreateTag();
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Taccola reload must commit the native stage-two loaded state");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "Taccola reload must consume exactly one iron ball");
        helper.assertTrue(loaded.getTagType(ArtilleryNativeState.POWDER_KEY) == Tag.TAG_DOUBLE,
                "Taccola powder must retain the native double NBT type");
        helper.assertTrue(loaded.getDouble(ArtilleryNativeState.POWDER_KEY) == 1.0D,
                "Taccola reload must set native powder one");
        helper.assertTrue(loaded.getTagType(ArtilleryNativeState.STAGE_KEY) == Tag.TAG_DOUBLE,
                "Taccola stage must retain the native double NBT type");
        helper.assertTrue(loaded.getDouble(ArtilleryNativeState.STAGE_KEY) == 2.0D,
                "Taccola reload must use native stage two");
        helper.assertTrue(loaded.getTagType(ArtilleryNativeState.AMMO_KEY) == Tag.TAG_DOUBLE,
                "Taccola must retain the native double ammo marker type");
        helper.assertTrue(loaded.getDouble(ArtilleryNativeState.AMMO_KEY) == 0.0D,
                "Taccola iron-ball branch must preserve native ammo code zero");
        helper.assertTrue(loaded.getBoolean(ArtilleryNativeState.LOADED_KEY),
                "Taccola reload must set the native loaded flag");

        BoomstickWeaponAdapter.ShotResult result = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit,
                weapon,
                recruit.position().add(10.0D, 0.0D, 0.0D));
        helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "a loaded Taccola Handgonne must fire its confirmed iron-ball branch");
        helper.assertTrue(result.projectilesSpawned() == 1,
                "Taccola iron-ball branch must spawn one native Ironball projectile");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "Taccola firing must clear its native loaded flag");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.POWDER_KEY) == 0.0D,
                "Taccola firing must clear native powder");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "Taccola firing must leave native stage three");
        helper.assertTrue(weapon.getOrCreateTag().getDouble(ArtilleryNativeState.AMMO_KEY) == 0.0D,
                "Taccola firing must preserve the native iron-ball ammo code");
        helper.assertFalse(weapon.getOrCreateTag().getBoolean(ArtilleryNativeState.LOADED_KEY),
                "Taccola firing must clear the native loaded flag");

        AbstractArrow projectile = helper.getLevel()
                .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .filter(candidate -> candidate.getOwner() == recruit
                        && candidate.getClass().getName()
                        .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the Taccola native projectile was not spawned"));
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - (Artillery1162Profiles.isInstalled() ? 4D : 1.85D)) < 1.0E-6D,
                "Taccola must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Taccola must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Taccola must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Taccola projectile must remain non-critical");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Taccola projectile must not be collectible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void artilleryTaccolaIronBallBranchRunsThroughTheCombatGoal(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.TACCOLA_HANDGONNE_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        ItemStack weapon = stack(SupportedArtillery.TACCOLA_HANDGONNE_ID);
        ItemStack ammo = stack(SupportedArtillery.IRON_BALL_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim an equipped Taccola Handgonne");
        goal.start();
        for (int tick = 0; tick < 70; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                "the Taccola combat path must consume its iron ball during reload");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the Taccola combat path must fire after reload");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .anyMatch(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity")),
                "the Taccola combat goal must spawn its native Ironball projectile");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryMatchlockMusketSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.MATCHLOCK_MUSKET_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryToradarRifleSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.TORADAR_RIFLE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryMatchlockPistolSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.MATCHLOCK_PISTOL_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryMatchlockCarbineSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.MATCHLOCK_CARBINE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryHarquebusSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.HARQUEBUS_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryHackbutSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.HACKBUT_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryHandgonneSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.HANDGONNE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryTaccolaHandgonneSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.TACCOLA_HANDGONNE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryHandCannonSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.HAND_CANNON_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryTillerGunSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.TILLER_GUN_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryMiniPistolaSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.MINI_PISTOLA_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryNobleHandgonneSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.NOBLE_HANDGONNE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryMarkmengonneSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.MARKMENGONNE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryDoubleBarrelGonneSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.DOUBLE_BARREL_GONNE_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryWindlassCrossbowSteppedReloadWalksTheNativeChain(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.WINDLASS_CROSSBOW_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryChuKoNuSteppedReloadFillsItsNativeMagazine(GameTestHelper helper) {
        assertNativeChainLoadsWeapon(helper, SupportedArtillery.CHU_KO_NU_ID);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryBareHandedRammingChainNeedsNoRamrod(GameTestHelper helper) {
        // CarbineRightclickProcedure rams with an empty hand, so a recruit without a ramrod must
        // still finish the chain.
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.MATCHLOCK_CARBINE_ID)
                || !artilleryItemRegistered(POWDER_FLASK_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_CARBINE_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        recruit.getInventory().addItem(stack(POWDER_FLASK_ID));
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.hasReloadComponents(recruit, weapon),
                "a bare-handed native ramming step must not demand a ramrod");

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "the Carbine chain must start without a ramrod");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
        }

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the bare-handed chain must leave the Carbine at its native stage-three ready state");
        helper.assertTrue(
                weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY) == 3.0D,
                "the Carbine must finish on the confirmed native stage 3.0");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void artillerySteppedReloadRefusesAMissingPowderFlask(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.MATCHLOCK_MUSKET_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.MATCHLOCK_MUSKET_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        recruit.getInventory().addItem(stack(RAMROD_ID));
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.hasReloadComponents(recruit, weapon),
                "a missing powder flask must leave the native chain unsatisfied");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, true),
                "a weapon that cannot complete its native chain must not report itself ready to load");

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertFalse(reloadGoal.canUse(), "the recruit must not start a chain it cannot finish");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "an unsatisfied chain must leave the weapon unloaded");
        helper.succeed();
    }

    /**
     * Drives one weapon's confirmed native loading chain through the recruit combat goal.
     *
     * <p>The recruit is given exactly the ammunition the chain spends plus one flask and one ramrod;
     * the chain must reach the profile's native loaded state, consume all of that ammunition, and
     * return every borrowed tool exactly once.</p>
     */
    private static void assertNativeChainLoadsWeapon(GameTestHelper helper, String weaponId) {
        ArtilleryWeaponProfile profile = SupportedArtillery.profileFor(weaponId).orElseThrow();
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(weaponId)
                || !artilleryItemRegistered(profile.ammoId())
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(weaponId);
        int ammoNeeded = ArtilleryReloadProtocol.ammoConsumed(weaponId);
        ItemStack ammo = stack(profile.ammoId(), ammoNeeded);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(ammo);
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        int expectedSteps = ArtilleryReloadProtocol.stepsFor(weaponId).size();
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.reloadStepCount(weapon) == expectedSteps,
                weaponId + " must walk its confirmed native loading chain");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.hasReloadComponents(recruit, weapon),
                weaponId + " must accept the components its native chain needs");
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.hasAmmo(recruit, weapon, true),
                weaponId + " must see exactly the ammunition its native chain spends");

        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                weaponId + " must start the chain unloaded");

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), weaponId + " must start its native loading chain");
        reloadGoal.start();

        boolean[] sawToolInOffhand = {false};
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon); tick++) {
            reloadGoal.tick();
            if (!recruit.getOffhandItem().isEmpty()) {
                sawToolInOffhand[0] = true;
            }
        }

        if (chainBorrowsATool(weaponId)) {
            helper.assertTrue(sawToolInOffhand[0],
                    weaponId + " must visibly hold each borrowed loading component");
        }
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(weapon),
                "the completed native chain must leave " + weaponId + " loaded");
        helper.assertTrue(
                ArtilleryNativeState.remainingRounds(weapon, profile) == profile.magazineSize(),
                weaponId + " must finish its chain with every native round committed, saw "
                        + ArtilleryNativeState.remainingRounds(weapon, profile));
        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "the borrowed off hand must be restored once " + weaponId + " finishes its chain");
        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                weaponId + " must spend exactly the ammunition its native chain consumes");
        // A borrowed tool must come back exactly once: the off hand is an inventory slot, so a
        // careless return would duplicate it.
        helper.assertTrue(recruit.getInventory().countItem(stack(POWDER_FLASK_ID).getItem()) == 1,
                "the powder flask is damaged by the native step, never consumed or duplicated");
        helper.assertTrue(recruit.getInventory().countItem(stack(RAMROD_ID).getItem()) == 1,
                "the ramrod is damaged by the native step, never consumed or duplicated");
        helper.succeed();
    }

    /**
     * A recruit that enters the level mid-reload must not stay stuck in the loading pose.
     *
     * <p>Copying a reloading recruit — a creative pick, a spawn egg carrying entity data, or a save
     * written mid-transaction — reproduces the weapon markers without the goal that owned them. The
     * combat goal refuses to start while the weapon reports itself reloading, so recovery has to
     * clear the markers or the copy freezes on the animation forever.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void recruitEnteringLevelMidReloadRecoversItsWeaponState(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack heldWeapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        ItemStack storedWeapon = stack(SupportedBoomsticks.ARBALEST_ID);
        MedievalBoomsticksAdapter.INSTANCE.setReloading(heldWeapon, true);
        MedievalBoomsticksAdapter.INSTANCE.setReloading(storedWeapon, true);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, heldWeapon);
        recruit.getInventory().addItem(storedWeapon);
        recruit.setShouldRanged(true);
        // The container stores a copy, so the spare must be re-read to assert on the stack recovery sees.
        ItemStack storedInInventory = findInInventory(recruit, storedWeapon);
        helper.assertTrue(MedievalBoomsticksAdapter.INSTANCE.isReloading(storedInInventory),
                "fixture must carry a spare weapon with an orphaned reload marker");

        RecruitBoomstickAttackGoal combatGoal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertFalse(combatGoal.canUse(),
                "fixture must reproduce the wedge: the combat goal stands down while the weapon reloads");

        BoomstickTransientStateRecovery.recover(recruit);

        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isReloading(heldWeapon),
                "recovery must clear the orphaned reload marker on the held weapon");
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isReloading(storedInInventory),
                "recovery must clear the orphaned reload marker on a stored spare weapon");
        helper.assertFalse(recruit.isUsingItem(),
                "recovery must end the item-use animation the interrupted reload started");
        helper.succeed();
    }

    /**
     * A recruit copied mid-chain must come back drivable, not frozen in the loading pose.
     *
     * <p>Reproduces a creative pick: the reloading recruit is serialized exactly as a spawn egg
     * carries it, restored under a fresh identity, and added to the level. The copy inherits the open
     * transaction — the reload marker, the borrowed ramrod, and the saved off hand — while the goal
     * that owned it stays with the original, so nothing would ever close it again.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void copyingAReloadingArtilleryRecruitLeavesTheCopyDrivable(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity original = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        original.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        original.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        giveNativeReloadTools(original);
        original.setTarget(null);
        original.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(original);
        helper.assertTrue(reloadGoal.canUse(), "fixture must start a native chain before it is copied");
        reloadGoal.start();
        reloadGoal.tick();
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isReloading(original.getMainHandItem()),
                "fixture must be copied while its loading transaction is still open");

        CompoundTag picked = new CompoundTag();
        helper.assertTrue(original.save(picked), "fixture recruit must serialize the way a pick copies it");

        Entity restored = EntityType.loadEntityRecursive(picked, helper.getLevel(), entity -> entity);
        if (!(restored instanceof CrossBowmanEntity copy)) {
            helper.fail("the copied entity data must restore a crossbowman");
            return;
        }
        // A spawned copy is a new entity, never the picked one under a second reference.
        copy.setUUID(UUID.randomUUID());
        copy.setPos(original.getX() + 1.0D, original.getY(), original.getZ());
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isReloading(copy.getMainHandItem()),
                "fixture must reproduce the wedge: the copy carries the open transaction before it joins");
        helper.getLevel().addFreshEntity(copy);

        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isReloading(copy.getMainHandItem()),
                "joining the level must close the inherited loading transaction");
        helper.assertTrue(copy.getOffhandItem().isEmpty(),
                "joining the level must return the inherited borrowed loading tool");

        copy.setTarget(null);
        copy.setShouldRanged(true);
        RecruitBoomstickAttackGoal copyGoal = RecruitBoomstickAttackGoal.passiveReload(copy);
        helper.assertTrue(copyGoal.canUse(), "the copy must be free to start a reload of its own");
        copyGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(copy.getMainHandItem()); tick++) {
            copyGoal.tick();
        }
        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(copy.getMainHandItem()),
                "the copy must finish a native chain instead of standing frozen in the loading pose");
        helper.succeed();
    }

    /**
     * Recovery must repair its own transaction and nothing else.
     *
     * <p>It runs for every registered adapter against whatever the recruit happens to hold, so an
     * adapter that cleared the transient loading lore unconditionally would strip the display lore
     * off any unrelated item a recruit carries in its main hand — every time it enters a level.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void recoveryLeavesUnrelatedDisplayLoreAlone(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack unrelated = stack("minecraft:iron_sword");
        ListTag lore = new ListTag();
        lore.add(net.minecraft.nbt.StringTag.valueOf(
                net.minecraft.network.chat.Component.Serializer.toJson(
                        net.minecraft.network.chat.Component.literal("Heirloom of the company"))));
        unrelated.getOrCreateTagElement("display").put("Lore", lore);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, unrelated);

        BoomstickTransientStateRecovery.recover(recruit);

        CompoundTag display = recruit.getMainHandItem().getTagElement("display");
        helper.assertTrue(display != null && display.contains("Lore", Tag.TAG_LIST),
                "recovery must not remove display lore from an item no adapter owns");
        helper.assertTrue(display.getList("Lore", Tag.TAG_STRING).size() == 1,
                "recovery must leave the unrelated lore line exactly as it was");
        helper.succeed();
    }

    /**
     * A recruit that dies mid-chain must still drop its own off-hand item.
     *
     * <p>An open chain parks the recruit's real off-hand item outside the inventory and shows the
     * borrowed loading tool in its place. Death never reaches the goal code that closes the chain,
     * so without a death-time repair the parked item would be destroyed with the entity.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recruitDyingMidChainKeepsItsSavedOffHand(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.ARQUEBUS_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID));
        giveNativeReloadTools(recruit);
        ItemStack ownOffhand = stack("minecraft:shield");
        recruit.getInventory().setItem(
                recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND), ownOffhand);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, ownOffhand);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(), "fixture must start a native chain before the recruit dies");
        reloadGoal.start();
        // The first tick only opens the reload window; the flask step commits on the tick after it.
        reloadGoal.tick();
        reloadGoal.tick();
        reloadGoal.tick();
        helper.assertFalse(recruit.getOffhandItem().is(ownOffhand.getItem()),
                "fixture must reproduce the wedge: the chain has parked the recruit's own off hand");

        recruit.die(recruit.damageSources().generic());

        helper.assertTrue(carriedOrDropped(helper, recruit, ownOffhand),
                "death must put the parked off-hand item back where the drop can find it");
        helper.assertFalse(ArtilleryAddonAdapter.INSTANCE.isReloading(weapon),
                "a dropped weapon must not retain a reload marker owned by the dead recruit");
        helper.succeed();
    }

    /** Whether the recruit still carries the item or dropped it into the world on death. */
    private static boolean carriedOrDropped(
            GameTestHelper helper,
            CrossBowmanEntity recruit,
            ItemStack expected
    ) {
        if (recruit.getOffhandItem().is(expected.getItem())
                || findInInventory(recruit, expected).is(expected.getItem())) {
            return true;
        }
        return helper.getLevel()
                .getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        recruit.getBoundingBox().inflate(6.0D))
                .stream()
                .anyMatch(dropped -> dropped.getItem().is(expected.getItem()));
    }

    /**
     * A finished chain must hand the off hand back empty, not holding its own loading tool.
     *
     * <p>The chain borrows the powder flask into the off hand and returns it to the inventory when
     * the next step starts. If that return lands in the off-hand slot itself, the recruit finishes
     * loaded but with a flask in hand, and every weapon whose native branch requires an empty or
     * fork-rest off hand then refuses the shot for the rest of the recruit's life.</p>
     */
    @GameTest(template = "empty", batch = "offHandRelease", timeoutTicks = 300)
    public static void repeatedNativeChainsLeaveTheOffHandFreeToFire(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HARQUEBUS_ID)
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.HARQUEBUS_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID, 4));
        giveNativeReloadTools(recruit);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        // Two cycles, because the failure needs a previous chain to have released its flask first:
        // one that hands the tool back into the off-hand slot poisons every chain after it.
        for (int cycle = 1; cycle <= 2; cycle++) {
            RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
            helper.assertTrue(reloadGoal.canUse(),
                    "cycle " + cycle + " must be able to start the native chain; the recruit holds "
                            + recruit.getOffhandItem() + " in its off hand");
            reloadGoal.start();
            for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon) + 2; tick++) {
                reloadGoal.tick();
            }
            reloadGoal.stop();

            helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(recruit.getMainHandItem()),
                    "cycle " + cycle + " must finish and leave the harquebus loaded");
            helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                    "cycle " + cycle + " must not leave a borrowed loading tool in the off hand,"
                            + " it holds " + recruit.getOffhandItem());

            BoomstickWeaponAdapter.ShotResult shot = ArtilleryAddonAdapter.INSTANCE.fire(
                    recruit, recruit.getMainHandItem(), recruit.position().add(6.0D, 1.0D, 0.0D));
            helper.assertTrue(shot.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                    "cycle " + cycle + " must end in a shot the native branch accepts, got "
                            + shot.outcome());
        }

        helper.assertTrue(recruit.getInventory().countItem(stack(POWDER_FLASK_ID).getItem()) == 1,
                "the powder flask must be neither lost nor duplicated across repeated chains");
        helper.succeed();
    }

    /**
     * A loading tool already sitting in the off hand must not wedge the recruit permanently.
     *
     * <p>This is the state a real server drifts into: a flask ends up in the off-hand slot, the
     * chain cannot borrow it from there, and the snapshot records it as the recruit's own equipment
     * and puts it straight back at the end of every chain. The recruit then loads perfectly and is
     * refused by the native branch gate on every single shot, forever.</p>
     */
    @GameTest(template = "empty", batch = "offHandRelease", timeoutTicks = 300)
    public static void aLoadingToolLeftInTheOffHandDoesNotWedgeTheRecruit(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HARQUEBUS_ID)
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }

        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.HARQUEBUS_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID, 4));
        recruit.getInventory().addItem(stack(RAMROD_ID));
        // The wedge: the only powder flask the recruit owns is already in its off hand.
        ItemStack strandedFlask = stack(POWDER_FLASK_ID);
        recruit.getInventory().setItem(
                recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND), strandedFlask);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, strandedFlask);
        recruit.setTarget(null);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal reloadGoal = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reloadGoal.canUse(),
                "the stranded flask still counts as a carried component, so the chain must start");
        reloadGoal.start();
        for (int tick = 0; tick <= ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon) + 2; tick++) {
            reloadGoal.tick();
        }
        reloadGoal.stop();

        helper.assertTrue(ArtilleryAddonAdapter.INSTANCE.isLoaded(recruit.getMainHandItem()),
                "the chain must reach the loaded state instead of aborting on the stranded flask");
        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "the stranded loading tool must be stowed, not handed back, it holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(recruit.getInventory().countItem(stack(POWDER_FLASK_ID).getItem()) == 1,
                "stowing the stranded flask must neither lose nor duplicate it");

        BoomstickWeaponAdapter.ShotResult shot = ArtilleryAddonAdapter.INSTANCE.fire(
                recruit, recruit.getMainHandItem(), recruit.position().add(6.0D, 1.0D, 0.0D));
        helper.assertTrue(shot.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                "the recruit must recover into a shot the native branch accepts, got " + shot.outcome());
        helper.succeed();
    }

    /**
     * The carry order has to reach a firearm the recruit only stores.
     *
     * <p>Recruits draw a weapon when a combat goal starts, so an idle company keeps its melee weapon
     * in hand and a firearm in the pack. The order exists to override exactly that.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderDrawsAStoredFirearm(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ARQUEBUS_ID));

        boolean carrying = BoomstickCarryOrder.apply(recruit, true);

        helper.assertTrue(carrying, "the order must report the drawn firearm");
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getMainHandItem()),
                "the recruit must draw the firearm into its main hand, which holds "
                        + recruit.getMainHandItem());
        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "drawing must not park the firearm in an equipment slot, the off hand holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(
                recruit.getInventory().countItem(stack("minecraft:iron_sword").getItem()) == 1,
                "the displaced melee weapon must stay in ordinary storage");
        helper.assertTrue(BoomstickCarryOrder.isCarrying(recruit),
                "the carry flag must be set so the client can render the carry state");
        helper.succeed();
    }

    /** The two separate orders must work repeatedly, not only on the recruit's initial loadout. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderCanDrawAgainAfterStowing(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ARQUEBUS_ID));

        BoomstickCarryOrder.apply(recruit, true);
        BoomstickCarryOrder.apply(recruit, false);
        helper.assertTrue(
                recruit.getOffhandItem().is(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()),
                "the away order must put the firearm into the shield hand, which holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the away order must restore the melee weapon to the main hand");
        boolean carrying = BoomstickCarryOrder.apply(recruit, true);

        helper.assertTrue(carrying, "the second draw order must report the firearm in hand");
        helper.assertTrue(
                recruit.getMainHandItem().is(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()),
                "drawing again must put the firearm back into the main hand, it holds "
                        + recruit.getMainHandItem());
        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "drawing again must leave the off hand free, it holds " + recruit.getOffhandItem());
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "repeated orders must keep exactly one firearm");
        helper.assertTrue(
                recruit.getInventory().countItem(stack("minecraft:iron_sword").getItem()) == 1,
                "repeated orders must keep exactly one melee weapon");
        helper.succeed();
    }

    /**
     * Stowing must hand the recruit its melee weapon back.
     *
     * <p>A recruit left with an empty main hand after the order would walk into its next fight
     * unarmed until a goal happens to equip it again.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderStowsTheFirearmAndRearmsTheRecruit(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ARQUEBUS_ID));
        BoomstickCarryOrder.apply(recruit, true);

        boolean carrying = BoomstickCarryOrder.apply(recruit, false);

        helper.assertFalse(carrying, "the order must report the stowed firearm");
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getOffhandItem()),
                "the stowed firearm must occupy the shield hand, which holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the melee weapon must still be in the main hand, it holds " + recruit.getMainHandItem());
        helper.assertFalse(BoomstickCarryOrder.isCarrying(recruit),
                "the carry flag must be cleared with the stowed firearm");
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "stowing must neither lose nor duplicate the firearm");
        helper.succeed();
    }

    /**
     * An unloaded Boomsticks firearm left in the off-hand slot is still drawable.
     *
     * <p>A player handing out weapons through the recruit's inventory screen can drop the gun into
     * either hand slot. Recruits' own {@code switchMainHandItem} starts its scan past both of them,
     * so a gun parked in the off hand is invisible to it; the order must still move every supported
     * Boomsticks firearm into the promised main hand without loading it or losing the displaced
     * weapon.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderDrawsAFirearmAlreadyInTheOffHand(GameTestHelper helper) {
        String[] weaponIds = {
                SupportedBoomsticks.ARQUEBUS_ID,
                SupportedBoomsticks.HANDGONNE_ID,
                SupportedBoomsticks.SPIKED_HANDGONNE_ID
        };
        for (int index = 0; index < weaponIds.length; index++) {
            String weaponId = weaponIds[index];
            CrossBowmanEntity recruit = spawnCrossbowman(helper, index + 1);
            ItemStack firearm = stack(weaponId);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
            recruit.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, firearm);
            helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(firearm),
                    weaponId + " must start unloaded in the off hand");

            boolean carrying = BoomstickCarryOrder.apply(recruit, true);

            helper.assertTrue(carrying, "the order must report the drawn " + weaponId);
            helper.assertTrue(recruit.getMainHandItem().is(stack(weaponId).getItem()),
                    weaponId + " must move into the main hand, it holds " + recruit.getMainHandItem());
            helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(recruit.getMainHandItem()),
                    weaponId + " must remain unloaded after the order");
            helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                    weaponId + " must leave the off hand, which holds " + recruit.getOffhandItem());
            helper.assertTrue(
                    recruit.getInventory()
                            .getItem(recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND))
                            .isEmpty(),
                    weaponId + " must leave its backing off-hand inventory slot");
            helper.assertTrue(recruit.getInventory().countItem(stack(weaponId).getItem()) == 1,
                    "the order must neither lose nor duplicate " + weaponId);
        }
        helper.succeed();
    }

    /**
     * A fight takes the weapon out of the off hand.
     *
     * <p>An older build or a player can leave the weapon where a shield goes, but firing and the
     * native loading chain both need it in the main hand — the chain borrows the off hand for its
     * own tools. Recruits' own {@code switchMainHandItem} starts its scan past both hand slots, so
     * the compatibility goal must recover that state itself.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void combatGoalDrawsTheCarriedFirearmOutOfTheOffHand(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        recruit.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                stack(SupportedBoomsticks.ARQUEBUS_ID));
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ROUND_BALL_ID, 4));
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getOffhandItem()),
                "the legacy fixture must start with the firearm in the off hand");
        recruit.setTarget(target);
        recruit.setShouldRanged(true);

        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "the combat goal must claim a recruit carrying a firearm");
        goal.start();
        for (int tick = 0; tick < 10; tick++) {
            goal.tick();
        }

        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getMainHandItem()),
                "the fight must move the firearm into the main hand, it holds "
                        + recruit.getMainHandItem());
        helper.assertFalse(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getOffhandItem()),
                "the off hand must be free for the loading chain's own tools, it holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "drawing into a fight must neither lose nor duplicate the firearm");
        helper.succeed();
    }

    /**
     * Stowing must not leave the main hand empty.
     *
     * <p>The weapon is worn in the off hand while the main hand keeps the melee weapon, but a
     * recruit given nothing but a gun stands with an empty main hand. Putting the gun away then has
     * to arm it with whatever it owns, or the order visibly disarms it.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderStowingRearmsAnEmptyMainHand(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ARQUEBUS_ID));
        recruit.getInventory().addItem(stack("minecraft:iron_sword"));
        BoomstickCarryOrder.apply(recruit, true);
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getMainHandItem()),
                "the order must have drawn the firearm into the main hand first");

        BoomstickCarryOrder.apply(recruit, false);

        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "stowing must arm the empty main hand, it holds " + recruit.getMainHandItem());
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getOffhandItem()),
                "stowing must move the firearm into the shield hand, which holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "stowing must neither lose nor duplicate the firearm");
        helper.succeed();
    }

    /**
     * The hand-gonne family is lit with a match, so the recruit shows one for the shot.
     *
     * <p>Natively a player holds the match in one hand and the gun in the other. A recruit keeps the
     * weapon in its main hand, so the match is mirrored into the off hand for the shot and given
     * straight back — nothing is spent, exactly as the native branch spends nothing.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void handgonneShowsTheNativeMatchForTheShot(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(SupportedArtillery.HANDGONNE_ID)
                || !artilleryItemRegistered(ArtilleryReloadProtocol.MATCH_ID)) {
            helper.succeed();
            return;
        }
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(SupportedArtillery.HANDGONNE_ID);
        recruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, weapon);
        recruit.getInventory().addItem(stack(ArtilleryReloadProtocol.MATCH_ID));

        ArtilleryAddonAdapter.INSTANCE.showFiringTool(recruit, weapon);

        helper.assertTrue(recruit.getOffhandItem().is(stack(ArtilleryReloadProtocol.MATCH_ID).getItem()),
                "the shot must show the native match, the off hand holds " + recruit.getOffhandItem());

        ArtilleryAddonAdapter.INSTANCE.clearFiringTool(recruit);

        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "the match must leave the hand with the shot, it holds " + recruit.getOffhandItem());
        helper.assertTrue(
                recruit.getInventory().countItem(stack(ArtilleryReloadProtocol.MATCH_ID).getItem()) == 1,
                "showing the match must neither spend nor duplicate it");
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            helper.assertTrue(
                    recruit.getInventory().getItem(recruit.getInventorySlotIndex(slot)).isEmpty(),
                    "a returned match must never occupy the " + slot + " armour slot");
        }

        // A weapon that is not lit by hand never borrows one: the matchlock family carries its cord
        // in the lock, so its own procedures never ask for a match.
        CrossBowmanEntity matchlockRecruit = spawnCrossbowman(helper, 2);
        ItemStack matchlock = stack(SupportedArtillery.ARQUEBUS_ID);
        matchlockRecruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, matchlock);
        matchlockRecruit.getInventory().addItem(stack(ArtilleryReloadProtocol.MATCH_ID));

        ArtilleryAddonAdapter.INSTANCE.showFiringTool(matchlockRecruit, matchlock);

        helper.assertTrue(matchlockRecruit.getOffhandItem().isEmpty(),
                "a matchlock must not borrow a match, its off hand holds "
                        + matchlockRecruit.getOffhandItem());

        CrossBowmanEntity noMatchRecruit = spawnCrossbowman(helper, 3);
        ItemStack noMatchWeapon = stack(SupportedArtillery.HANDGONNE_ID);
        ItemStack shield = stack("minecraft:shield");
        noMatchRecruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, noMatchWeapon);
        noMatchRecruit.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shield);

        ArtilleryAddonAdapter.INSTANCE.showFiringTool(noMatchRecruit, noMatchWeapon);
        ArtilleryAddonAdapter.INSTANCE.clearFiringTool(noMatchRecruit);

        helper.assertTrue(noMatchRecruit.getOffhandItem().is(shield.getItem()),
                "a missing match must leave the original off-hand item in place");
        helper.assertTrue(noMatchRecruit.getInventory().countItem(shield.getItem()) == 1,
                "a failed match display must not duplicate the off-hand item");
        helper.succeed();
    }

    /** Saved recruits from the faulty return path are repaired without losing real armour. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void invalidInventoryStacksAreRemovedFromArmourSlots(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack helmet = stack("minecraft:iron_helmet");
        ItemStack stray = stack("minecraft:stick");
        recruit.setItemSlot(EquipmentSlot.HEAD, helmet);
        // Reproduces the old direct-container write: the mirror is corrupted while the real
        // equipment list still contains the helmet.
        recruit.getInventory().setItem(
                recruit.getInventorySlotIndex(EquipmentSlot.HEAD), stray);
        recruit.getInventory().setItem(
                recruit.getInventorySlotIndex(EquipmentSlot.CHEST), stack("minecraft:cobblestone"));

        boolean repaired = RecruitInventorySafety.repairInvalidArmour(recruit);

        helper.assertTrue(repaired, "the invalid saved equipment mirror must be detected");
        helper.assertTrue(recruit.getItemBySlot(EquipmentSlot.HEAD).is(helmet.getItem()),
                "repair must preserve the real helmet");
        helper.assertTrue(
                recruit.getInventory()
                        .getItem(recruit.getInventorySlotIndex(EquipmentSlot.HEAD))
                        .is(helmet.getItem()),
                "the repaired helmet mirror must agree with the real equipment slot");
        helper.assertTrue(
                recruit.getInventory()
                        .getItem(recruit.getInventorySlotIndex(EquipmentSlot.CHEST))
                        .isEmpty(),
                "an ordinary block must be removed from the chest slot");
        helper.assertTrue(recruit.getInventory().countItem(stray.getItem()) == 1,
                "the displaced stack must be returned to storage exactly once");
        helper.assertTrue(recruit.getInventory().countItem(stack("minecraft:cobblestone").getItem()) == 1,
                "the other displaced stack must be returned to storage exactly once");
        for (int slot = 0; slot < recruit.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = recruit.getInventory().getItem(slot);
            if (candidate.is(stray.getItem()) || candidate.is(stack("minecraft:cobblestone").getItem())) {
                helper.assertTrue(RecruitInventorySafety.isStorageSlot(recruit, slot),
                        "recovered ordinary items must only occupy storage slots, found index " + slot);
            }
        }
        helper.succeed();
    }

    /**
     * A round trip through both orders must not lose or duplicate anything.
     *
     * <p>Drawing replaces the main-hand weapon, while stowing moves the firearm into the shield
     * hand and restores the original weapon. Neither direction may duplicate a stack.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderRoundTripKeepsExactlyOneFirearm(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ARQUEBUS_ID));

        BoomstickCarryOrder.apply(recruit, true);
        boolean carrying = BoomstickCarryOrder.apply(recruit, false);

        helper.assertFalse(carrying, "the order must report the stowed firearm");
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getOffhandItem()),
                "the stowed firearm must be in the shield hand, which holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the melee weapon must never leave the main hand, it holds " + recruit.getMainHandItem());
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "the round trip must neither lose nor duplicate the firearm");

        // Storage full: the weapon cannot leave the off hand without dropping something, so the
        // recruit is left exactly as it stood rather than half stowed.
        CrossBowmanEntity fullRecruit = spawnCrossbowman(helper, 2);
        fullRecruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        fullRecruit.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                stack(SupportedBoomsticks.ARQUEBUS_ID));
        for (int slot = 0; slot < fullRecruit.getInventory().getContainerSize(); slot++) {
            if (fullRecruit.getEquipmentSlotIndex(slot) == null) {
                fullRecruit.getInventory().setItem(slot, stack("minecraft:cobblestone"));
            }
        }

        BoomstickCarryOrder.apply(fullRecruit, false);

        helper.assertTrue(
                fullRecruit.getOffhandItem().is(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()),
                "a firearm with nowhere to go must stay in the off hand, it holds "
                        + fullRecruit.getOffhandItem());
        helper.assertTrue(fullRecruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the blocked stow must not disturb the main hand, it holds "
                        + fullRecruit.getMainHandItem());

        CrossBowmanEntity rotatedRecruit = spawnCrossbowman(helper, 3);
        rotatedRecruit.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                stack(SupportedBoomsticks.ARQUEBUS_ID));
        rotatedRecruit.setItemInHand(
                net.minecraft.world.InteractionHand.OFF_HAND,
                stack("minecraft:shield"));
        int replacementSlot = -1;
        for (int slot = 0; slot < rotatedRecruit.getInventory().getContainerSize(); slot++) {
            if (!RecruitInventorySafety.isStorageSlot(rotatedRecruit, slot)) {
                continue;
            }
            if (replacementSlot < 0) {
                replacementSlot = slot;
            }
            rotatedRecruit.getInventory().setItem(slot, stack("minecraft:cobblestone"));
        }
        helper.assertTrue(replacementSlot >= 0, "fixture needs one ordinary storage slot");
        rotatedRecruit.getInventory().setItem(replacementSlot, stack("minecraft:iron_sword"));

        boolean rotatedCarrying = BoomstickCarryOrder.apply(rotatedRecruit, false);

        helper.assertFalse(rotatedCarrying,
                "a full inventory must still stow through a lossless three-stack rotation");
        helper.assertTrue(rotatedRecruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the replacement sword must move into the main hand");
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(rotatedRecruit.getOffhandItem()),
                "the firearm must move into the off hand even when storage is full");
        helper.assertTrue(rotatedRecruit.getInventory().countItem(stack("minecraft:shield").getItem()) == 1,
                "the displaced shield must occupy the sword's old slot without being lost");
        helper.assertTrue(
                rotatedRecruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "the full-inventory rotation must neither lose nor duplicate the firearm");
        helper.succeed();
    }

    /** The inverse carry order restores the exact Epic Knights shield it displaced. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderRestoresTheDisplacedEpicKnightsShield(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack firearm = stack(SupportedBoomsticks.ARQUEBUS_ID);
        ItemStack shield = stack("magistuarmory:iron_heatershield");
        shield.setDamageValue(3);
        recruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, firearm);
        recruit.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shield);

        int swordSlot = -1;
        for (int slot = 0; slot < recruit.getInventory().getContainerSize(); slot++) {
            if (!RecruitInventorySafety.isStorageSlot(recruit, slot)) {
                continue;
            }
            if (swordSlot < 0) {
                swordSlot = slot;
                recruit.getInventory().setItem(slot, stack("minecraft:iron_sword"));
            } else {
                recruit.getInventory().setItem(slot, stack("minecraft:cobblestone"));
            }
        }
        helper.assertTrue(swordSlot >= 0, "fixture needs one ordinary storage slot");

        BoomstickCarryOrder.apply(recruit, false);

        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the away order must restore the melee weapon");
        helper.assertTrue(recruit.getOffhandItem().is(firearm.getItem()),
                "the away order must stow the firearm in the off hand");
        helper.assertTrue(recruit.getInventory().countItem(shield.getItem()) == 1,
                "the displaced Epic Knights shield must remain in storage exactly once");

        boolean carrying = BoomstickCarryOrder.apply(recruit, true);

        helper.assertTrue(carrying, "the out order must draw the firearm again");
        helper.assertTrue(recruit.getMainHandItem().is(firearm.getItem()),
                "the firearm must return to the main hand");
        helper.assertTrue(recruit.getOffhandItem().is(shield.getItem()),
                "the exact displaced Epic Knights shield must return to the off hand");
        helper.assertTrue(recruit.getOffhandItem().getDamageValue() == 3,
                "restoring the shield must preserve its damage and NBT");
        helper.assertTrue(recruit.getInventory().countItem(shield.getItem()) == 1,
                "the round trip must keep exactly one Epic Knights shield");
        helper.assertTrue(recruit.getInventory().countItem(firearm.getItem()) == 1,
                "the round trip must keep exactly one firearm");
        helper.assertTrue(recruit.getInventory().countItem(stack("minecraft:iron_sword").getItem()) == 1,
                "the round trip must keep exactly one melee weapon");
        helper.succeed();
    }

    /**
     * A recruit that owns nothing else still puts its only firearm away.
     *
     * <p>The weapon goes over the shield hand the way a player's off hand carries one and the main
     * hand is left empty. The combat goal draws it back out of the off hand when a fight starts, so
     * an empty hand costs the recruit nothing.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderStowsTheOnlyFirearmIntoTheShieldHand(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                stack(SupportedBoomsticks.ARQUEBUS_ID));

        boolean carrying = BoomstickCarryOrder.apply(recruit, false);

        helper.assertFalse(carrying, "the order must report the stowed firearm");
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getOffhandItem()),
                "the only firearm must move into the shield hand, which holds "
                        + recruit.getOffhandItem());
        helper.assertTrue(recruit.getMainHandItem().isEmpty(),
                "with nothing to take instead the main hand must be left empty, it holds "
                        + recruit.getMainHandItem());
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "stowing must neither lose nor duplicate the firearm");
        helper.assertFalse(BoomstickCarryOrder.isCarrying(recruit),
                "the carry flag must clear once the weapon is off the main hand");
        helper.succeed();
    }

    /**
     * A stow that would have to drop something leaves the recruit exactly as it stood.
     *
     * <p>The shield hand is occupied and storage is full, so the firearm has nowhere to go.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderKeepsTheFirearmWhenNothingCanMove(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                stack(SupportedBoomsticks.ARQUEBUS_ID));
        recruit.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack("minecraft:shield"));
        for (int slot = 0; slot < recruit.getInventory().getContainerSize(); slot++) {
            if (RecruitInventorySafety.isStorageSlot(recruit, slot)) {
                recruit.getInventory().setItem(slot, stack("minecraft:cobblestone"));
            }
        }

        boolean carrying = BoomstickCarryOrder.apply(recruit, false);

        helper.assertTrue(carrying, "a blocked stow must report the weapon as still carried");
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getMainHandItem()),
                "the blocked stow must leave the firearm in the main hand, which holds "
                        + recruit.getMainHandItem());
        helper.assertTrue(recruit.getOffhandItem().is(stack("minecraft:shield").getItem()),
                "the blocked stow must not disturb the shield hand, it holds "
                        + recruit.getOffhandItem());
        helper.succeed();
    }

    /**
     * A gunner without a melee weapon takes its crossbow back.
     *
     * <p>A crossbowman is allowed to hold its crossbow, so that is what it falls back to when it owns
     * no sword or axe.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderStowingFallsBackToTheCrossbow(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                stack(SupportedBoomsticks.ARQUEBUS_ID));
        recruit.getInventory().addItem(stack("minecraft:crossbow"));

        BoomstickCarryOrder.apply(recruit, false);

        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:crossbow").getItem()),
                "the recruit must fall back to its crossbow, it holds " + recruit.getMainHandItem());
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getOffhandItem()),
                "the stowed firearm must remain visible in the shield hand");
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "stowing must neither lose nor duplicate the firearm");
        helper.succeed();
    }

    /**
     * The order must reach the gun, not the loading kit stored next to it.
     *
     * <p>A real recruit carries powder, ammunition, and a ramrod alongside the weapon, and the
     * equipment slot the order writes is backed by an inventory slot. A component that ends up in the
     * hand instead would disarm the recruit and strand the chain that owns the component.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderDrawsTheGunOutOfAFullLoadingKit(GameTestHelper helper) {
        if (!ArtilleryAddonAdapter.INSTANCE.isAvailable()
                || !artilleryItemRegistered(POWDER_FLASK_ID)
                || !artilleryItemRegistered(RAMROD_ID)) {
            helper.succeed();
            return;
        }
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        recruit.getInventory().addItem(stack(POWDER_FLASK_ID));
        recruit.getInventory().addItem(stack(SupportedArtillery.IRON_BALL_ID, 32));
        recruit.getInventory().addItem(stack(RAMROD_ID));
        recruit.getInventory().addItem(stack(SupportedArtillery.ARQUEBUS_ID));

        BoomstickCarryOrder.apply(recruit, true);

        helper.assertTrue(recruit.getMainHandItem().is(stack(SupportedArtillery.ARQUEBUS_ID).getItem()),
                "the order must draw the gun, it drew " + recruit.getOffhandItem());
        helper.assertTrue(
                recruit.getInventory()
                        .getItem(recruit.getInventorySlotIndex(EquipmentSlot.MAINHAND))
                        .is(stack(SupportedArtillery.ARQUEBUS_ID).getItem()),
                "the inventory slot backing the main hand must show the same gun, it shows "
                        + recruit.getInventory().getItem(recruit.getInventorySlotIndex(EquipmentSlot.MAINHAND)));
        helper.assertTrue(recruit.getOffhandItem().isEmpty(),
                "the loading hand must remain empty, it holds " + recruit.getOffhandItem());
        helper.assertTrue(recruit.getInventory().countItem(stack(POWDER_FLASK_ID).getItem()) == 1,
                "the powder flask must stay stored");
        helper.assertTrue(recruit.getInventory().countItem(stack(RAMROD_ID).getItem()) == 1,
                "the ramrod must stay stored");
        helper.assertTrue(recruit.getInventory().countItem(stack(SupportedArtillery.IRON_BALL_ID).getItem()) == 32,
                "the ammunition must stay stored");
        helper.succeed();
    }

    /**
     * A recruit that owns no firearm must be left exactly as it was.
     *
     * <p>The order runs over a whole group, so it reaches recruits that carry nothing it can draw.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderLeavesARecruitWithoutAFirearmAlone(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));

        boolean carrying = BoomstickCarryOrder.apply(recruit, true);

        helper.assertFalse(carrying, "there is no firearm to draw, so the order must report nothing");
        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the melee weapon must stay in hand, it holds " + recruit.getMainHandItem());
        helper.assertFalse(BoomstickCarryOrder.isCarrying(recruit),
                "an unfulfilled order must not set the carry flag");
        helper.succeed();
    }

    /**
     * Whether the chain borrows a component that survives its step.
     *
     * <p>A consumed ball or arrow disappears inside the same step, and a native bare-hand branch
     * borrows nothing at all, so only a damaged tool is ever visible in the off hand.</p>
     */
    private static boolean chainBorrowsATool(String weaponId) {
        return ArtilleryReloadProtocol.stepsFor(weaponId).stream()
                .anyMatch(step -> step.componentUse() == ArtilleryReloadStep.ComponentUse.DAMAGE_ONE
                        && !step.components().get(0).isEmptyHand());
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryWheellockPistol(GameTestHelper helper) {
        assertWheellockCycle(helper, SupportedArtillery.WHEELLOCK_PISTOL_ID, 3.0D, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryWheellockMusket(GameTestHelper helper) {
        assertWheellockCycle(helper, SupportedArtillery.WHEELLOCK_MUSKET_ID, 5.7D, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryWheellockHuntingRifle(GameTestHelper helper) {
        assertWheellockCycle(helper, SupportedArtillery.WHEELLOCK_HUNTING_RIFLE_ID, 6.2D, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryWheellockBreechloader(GameTestHelper helper) {
        assertWheellockCycle(helper, SupportedArtillery.WHEELLOCK_BREECHLOADING_RIFLE_ID, 6.2D, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryDualWheellockPistol(GameTestHelper helper) {
        assertWheellockCycle(helper, SupportedArtillery.DUAL_WHEELLOCK_PISTOL_ID, 3.0D, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void artilleryDualWheellockCarbine(GameTestHelper helper) {
        assertWheellockCycle(helper, SupportedArtillery.DUAL_WHEELLOCK_CARBINE_ID, 3.6D, 2);
    }

    private static void assertWheellockCycle(GameTestHelper helper, String weaponId, double damage, int rounds) {
        if (!Artillery1162Profiles.isInstalled()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(artilleryItemRegistered(weaponId), "1.16.2 must register " + weaponId);
        var adapter = ArtilleryAddonAdapter.INSTANCE;
        var profile = SupportedArtillery.profileFor(weaponId).orElseThrow();
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        ItemStack weapon = stack(weaponId);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.setShouldRanged(true);
        recruit.getInventory().addItem(stack(profile.ammoId(), rounds));
        giveNativeReloadTools(recruit);
        helper.assertFalse(adapter.hasReloadComponents(recruit, weapon), "a wheellock must require its spanner");
        ItemStack spanner = stack(SupportedArtillery.MOD_ID + ":iron_wheellock_spanner");
        recruit.getInventory().addItem(spanner);
        if (rounds == 2) {
            ItemStack partial = weapon.copy();
            partial.getOrCreateTag().putDouble("barrel_one", 2);
            partial.getOrCreateTag().putDouble("rammed_one", 0);
            partial.getOrCreateTag().putDouble("barrel_two", 2);
            partial.getOrCreateTag().putDouble("rammed_two", 0);
            CompoundTag savedPartial = partial.getTag().copy();
            helper.assertFalse(adapter.hasReloadComponents(recruit, partial),
                    "a noncanonical partial second barrel must not be overwritten by a new chain");
            helper.assertFalse(adapter.applyReloadStep(recruit, partial, 2),
                    "a direct reload step must also refuse the noncanonical partial state");
            helper.assertTrue(savedPartial.equals(partial.getTag()), "refusal must preserve both paid barrels");
        }
        RecruitBoomstickAttackGoal reload = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reload.canUse(), "the wheellock must enter recruit reload AI");
        reload.start();
        for (int tick = 0; tick < adapter.reloadTicks(weapon) / 2; tick++) reload.tick();
        reload.stop();
        weapon = ItemStack.of(weapon.save(new CompoundTag()));
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        reload = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reload.canUse(), "an interrupted wheellock chain must resume after save/load");
        reload.start();
        for (int tick = 0; tick <= adapter.reloadTicks(weapon); tick++) reload.tick();
        helper.assertTrue(adapter.isLoaded(weapon), "the complete chain must leave the wheellock ready");
        helper.assertTrue(ArtilleryNativeState.remainingRounds(weapon, profile) == rounds, "all paid barrels must remain");
        helper.assertTrue(recruit.getInventory().countItem(stack(profile.ammoId()).getItem()) == 0,
                "reload must consume exactly one physical round per barrel");
        helper.assertTrue(recruit.getInventory().countItem(spanner.getItem()) == 1, "the spanner must return exactly once");
        helper.assertTrue(findInInventory(recruit, spanner).getDamageValue() == (rounds == 2 ? 0 : 1),
                "winding must preserve the native single/dual spanner wear boundary");
        boolean breechloader = weaponId.equals(SupportedArtillery.WHEELLOCK_BREECHLOADING_RIFLE_ID);
        helper.assertTrue(findInInventory(recruit, stack(RAMROD_ID)).getDamageValue()
                        == (breechloader || rounds == 2 ? 0 : 1),
                "ramming must charge native wear once, including after an interrupted reload");
        helper.assertTrue(findInInventory(recruit, stack(POWDER_FLASK_ID)).getDamageValue()
                        == (breechloader ? 0 : rounds),
                "powder must be paid once per muzzleloaded barrel");
        helper.assertTrue(recruit.getOffhandItem().isEmpty(), "reload must return the borrowed off hand");
        for (int shot = 0; shot < rounds; shot++) {
            // Save/load the weapon between barrels, without reloading or spending another ball.
            weapon = ItemStack.of(weapon.save(new CompoundTag()));
            recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            recruit.getRandom().setSeed(0);
            var result = adapter.fire(recruit, weapon, recruit.position().add(10, 0, 0));
            helper.assertTrue(result.outcome() == BoomstickWeaponAdapter.ShotOutcome.FIRED,
                    "each loaded barrel must fire through the server adapter");
            helper.assertTrue(result.projectilesSpawned() == 1, "each trigger pull must spawn one ball");
            helper.assertTrue(ArtilleryNativeState.remainingRounds(weapon, profile) == rounds - shot - 1,
                    "firing must spend only the selected barrel");
        }
        var projectiles = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24))
                .stream().filter(p -> p.getOwner() == recruit).toList();
        helper.assertTrue(projectiles.size() == rounds, "the server must own one native projectile per round");
        for (var projectile : projectiles) {
            helper.assertTrue(projectile.getClass().getName().equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"),
                    "the native Ironball class must be preserved");
            helper.assertTrue(Math.abs(projectile.getBaseDamage() - damage) < 1e-6, "1.16.2 native damage must match");
            helper.assertTrue(projectile.getPierceLevel() == 0 && !projectile.isCritArrow() && projectile.isSilent(),
                    "wheel locks use the native noncritical, nonpiercing, silent shot");
        }
        if (weaponId.equals(SupportedArtillery.WHEELLOCK_BREECHLOADING_RIFLE_ID)) {
            long cases = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    recruit.getBoundingBox().inflate(2)).stream()
                    .filter(item -> item.getItem().is(stack(SupportedArtillery.EMPTY_CARTRIDGE_ID).getItem())).count();
            helper.assertTrue(cases == 1, "a spent cartridge must produce exactly one recoverable empty case");
        }
        recruit.getInventory().addItem(stack(profile.ammoId(), rounds));
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 1);
        recruit.setTarget(target);
        RecruitBoomstickAttackGoal combat = new RecruitBoomstickAttackGoal(recruit, 1);
        helper.assertTrue(combat.canUse(), "the wheellock must enter recruit combat AI");
        combat.start();
        for (int tick = 0; tick < 120; tick++) {
            recruit.getRandom().setSeed(0);
            combat.tick();
        }
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24))
                .stream().filter(p -> p.getOwner() == recruit).count() > rounds, "combat AI must reload and fire again");
        combat.stop();
        helper.succeed();
    }

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

    /**
     * Supplies the native loading tools a captured multi-step chain requires.
     *
     * <p>The powder flask and the ramrod are damaged rather than consumed, so one of each covers a
     * whole test run.</p>
     */
    private static void giveNativeReloadTools(CrossBowmanEntity recruit) {
        recruit.getInventory().addItem(stack(POWDER_FLASK_ID));
        recruit.getInventory().addItem(stack(RAMROD_ID));
    }

    /** Returns the stack the recruit's container really holds, which is a copy of what was added. */
    private static ItemStack findInInventory(CrossBowmanEntity recruit, ItemStack added) {
        for (int slot = 0; slot < recruit.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = recruit.getInventory().getItem(slot);
            if (candidate.is(added.getItem())) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack stack(String registryId, int count) {
        ItemStack stack = stack(registryId);
        stack.setCount(count);
        return stack;
    }

    private static boolean artilleryItemRegistered(String registryId) {
        Item item = ForgeRegistries.ITEMS.getValue(id(registryId));
        return item != null && item != Items.AIR;
    }

    private static boolean artilleryEntityRegistered(String registryId) {
        return ForgeRegistries.ENTITY_TYPES.getValue(id(registryId)) != null;
    }

    /** Calls the optional formation mod's final heading write after Mixins have transformed it. */
    private static boolean applyBetterFormationHeading(CrossBowmanEntity recruit, float heading) {
        try {
            Class<?> controller = Class.forName(
                    "me.puredoom.betterformations.manager.FormationController");
            Method faceHeading = controller.getDeclaredMethod(
                    "faceHeading",
                    Class.forName("com.talhanation.recruits.entities.AbstractRecruitEntity"),
                    float.class);
            faceHeading.setAccessible(true);
            faceHeading.invoke(null, recruit, heading);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not exercise BetterRecruitFormations heading", exception);
        }
    }

    /** Keeps optional Medieval Boomsticks implementation types out of the GameTest holder's linkage. */
    private static AbstractArrow medievalProjectile(
            String className,
            Level level,
            LivingEntity owner,
            ItemStack weapon) {
        try {
            Class<?> projectileClass = Class.forName(className);
            Constructor<?> constructor = projectileClass.getConstructor(
                    Level.class, LivingEntity.class, ItemStack.class);
            Object projectile = constructor.newInstance(level, owner, weapon);
            if (projectile instanceof AbstractArrow arrow) {
                return arrow;
            }
            throw new IllegalStateException(className + " is not an AbstractArrow");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not create optional projectile " + className, exception);
        }
    }

    private static boolean isNamedType(Object value, String className) {
        return value != null && value.getClass().getName().equals(className);
    }

    private static float medievalJavelinSpeed() {
        try {
            Class<?> configClass = Class.forName(MEDIEVAL_BOOMSTICKS_CONFIG_CLASS);
            Field speed = configClass.getField("javelinSpeed");
            return (float) speed.getDouble(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not read Medieval Boomsticks javelin speed", exception);
        }
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid registry ID " + value);
        }
        return id;
    }

    private static boolean isRunning(CrossBowmanEntity recruit, Goal goal) {
        return recruit.goalSelector.getRunningGoals().anyMatch(wrapped -> wrapped.getGoal() == goal);
    }

    private static final class TrackingMoveGoal extends Goal {
        private TrackingMoveGoal() {
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }
    }

    private static final class BlockingMoveGoal extends Goal {
        private BlockingMoveGoal() {
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
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
