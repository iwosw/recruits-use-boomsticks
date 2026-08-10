package org.iwoss.recruits_use_boomsticks.gametest;

import com.TBK.medieval_boomsticks.common.items.RechargeItem;
import com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.entities.ai.FleeTNT;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickAttackState;
import org.iwoss.recruits_use_boomsticks.command.BoomstickCarryOrder;
import org.iwoss.recruits_use_boomsticks.ai.RecruitBoomstickAttackGoal;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickAmmoAccess;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickTransientStateRecovery;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryAddonAdapter;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryNativeState;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadProtocol;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadStep;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.compat.SupportedArtillery;
import org.iwoss.recruits_use_boomsticks.compat.MedievalBoomsticksAdapter;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

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

        recruit.setTarget(helper.spawn(EntityType.ZOMBIE, 3, 2, 1));
        helper.assertTrue(reloadGoal.canContinueToUse(),
                "acquiring an attacker must not cancel an in-progress reload");
        helper.assertFalse(new RecruitBoomstickAttackGoal(recruit, 1.0D).canUse(),
                "combat goal must wait for the in-progress reload");
        reloadGoal.tick();
        helper.assertTrue(reloadGoal.phase() == BoomstickAttackState.Phase.RELOAD,
                "reload progress must continue after the recruit is attacked");
        helper.assertTrue(RechargeItem.isReCharge(weapon),
                "reload animation must remain active after the recruit is attacked");
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

        HeavyBoltProjectile bolt = helper.getLevel()
                .getEntitiesOfClass(HeavyBoltProjectile.class, recruit.getBoundingBox().inflate(24.0D))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("arbalest did not spawn a heavy bolt"));
        helper.assertTrue(bolt.getDeltaMovement().y > 0.25D,
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
    public static void expiredRecruitProjectilesAreRemovedWithoutChangingPlayerProjectiles(GameTestHelper helper) {
        CrossBowmanEntity shooter = spawnCrossbowman(helper);
        Player player = helper.makeMockPlayer();
        ItemStack weapon = stack(SupportedBoomsticks.HANDGONNE_ID);
        AbstractArrow recruitProjectile = new RoundBallProjectile(helper.getLevel(), shooter, weapon);
        AbstractArrow playerProjectile = new RoundBallProjectile(helper.getLevel(), player, weapon);
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
        AbstractArrow airborne = new HeavyBoltProjectile(helper.getLevel(), shooter, weapon);
        airborne.setOwner(shooter);
        airborne.pickup = AbstractArrow.Pickup.ALLOWED;
        airborne.tickCount = 200;

        airborne.tick();

        helper.assertTrue(airborne.isRemoved(),
                "an airborne recruit heavy bolt must not bypass the compatibility TTL");
        helper.succeed();
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

        recruit.setTarget(helper.spawn(EntityType.ZOMBIE, 5, 2, 1));
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

        recruit.setTarget(null);
        recruit.goalSelector.tick();
        helper.assertFalse(isRunning(recruit, reloadGoal),
                "a loaded weapon must not restart passive reload after losing the target");
        helper.assertTrue(isRunning(recruit, moveGoal),
                "combat-to-passive transition must release the selector's MOVE lock");
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
        for (int tick = 0; tick < 20 && !RechargeItem.isFire(previousWeapon); tick++) {
            goal.tick();
        }
        helper.assertTrue(RechargeItem.isFire(previousWeapon), "fixture must reach the firing animation");

        recruit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        goal.tick();

        helper.assertFalse(RechargeItem.isFire(previousWeapon),
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
    public static void onlyLegacyLoadedNbtIsNormalized(GameTestHelper helper) {
        ItemStack legacy = stack(SupportedBoomsticks.HANDGONNE_ID);
        RechargeItem.setCharged(legacy, true);
        helper.assertTrue(MedievalBoomsticksAdapter.INSTANCE.isLoaded(legacy),
                "legacy Charged=true must be normalized into native payload");
        helper.assertTrue(legacy.getOrCreateTag().getList("ChargedProjectiles", Tag.TAG_COMPOUND).size() == 1,
                "legacy handgonne must receive one native charged projectile");

        ItemStack malformed = stack(SupportedBoomsticks.SPIKED_HANDGONNE_ID);
        RechargeItem.setCharged(malformed, true);
        malformed.getOrCreateTag().putString("ChargedProjectiles", "invalid");
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(malformed),
                "an explicitly malformed charged payload must be rejected");
        helper.assertFalse(RechargeItem.isCharged(malformed),
                "rejecting malformed payload must clear Charged");
        helper.assertFalse(malformed.getOrCreateTag().contains("ChargedProjectiles"),
                "rejecting malformed payload must remove it");

        ListTag wrongCount = new ListTag();
        wrongCount.add(stack(SupportedBoomsticks.ROUND_BALL_ID).save(new CompoundTag()));
        RechargeItem.setCharged(malformed, true);
        malformed.getOrCreateTag().put("ChargedProjectiles", wrongCount);
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(malformed),
                "a charged payload with the wrong projectile count must be rejected");
        helper.assertFalse(RechargeItem.isCharged(malformed),
                "wrong projectile count must not create free ammunition");

        ListTag wrongAmmo = new ListTag();
        for (int index = 0; index < 3; index++) {
            wrongAmmo.add(stack(SupportedBoomsticks.HEAVY_BOLT_ID).save(new CompoundTag()));
        }
        RechargeItem.setCharged(malformed, true);
        malformed.getOrCreateTag().put("ChargedProjectiles", wrongAmmo);
        helper.assertFalse(MedievalBoomsticksAdapter.INSTANCE.isLoaded(malformed),
                "a charged payload containing the wrong ammo item must be rejected");
        helper.assertFalse(RechargeItem.isCharged(malformed),
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
        RechargeItem.setCharged(nativeLoaded, true);

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
            helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.0D) < 1.0E-6D,
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
        for (int tick = 0; tick < 160; tick++) {
            goal.tick();
        }

        helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) < 30,
                "the combat goal must consume the one iron ball each native Hand Cannon volley loads");
        helper.assertTrue(
                helper.getLevel()
                        .getEntitiesOfClass(AbstractArrow.class, recruit.getBoundingBox().inflate(24.0D))
                        .stream()
                        .filter(projectile -> projectile.getOwner() == recruit
                                && projectile.getClass().getName()
                                .equals("net.mcreator.artilleryaddon.entity.IronballProjectileEntity"))
                        .count() >= 3,
                "the combat goal must spawn a native three-projectile Hand Cannon volley");
        helper.succeed();
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.7D) < 1.0E-6D,
                "Double Barrel projectile must preserve its confirmed base damage");
        helper.assertFalse(projectile.isSilent(),
                "Double Barrel projectile must preserve the native audible flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Double Barrel projectile must preserve native knockback");
        helper.assertTrue(projectile.getPierceLevel() == 1,
                "Double Barrel projectile must preserve native piercing");
        helper.assertTrue(projectile.isCritArrow(),
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 4.5D) < 1.0E-6D,
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.7D) < 1.0E-6D,
                "Matchlock Carbine must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Matchlock Carbine must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Matchlock Carbine must preserve native projectile knockback");
        helper.assertFalse(projectile.isCritArrow(),
                "Matchlock Carbine must preserve the native non-critical projectile flag");
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.DISALLOWED,
                "Matchlock Carbine projectile must not be collectible");
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.7D) < 1.0E-6D,
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 1.9D) < 1.0E-6D,
                "Matchlock Pistol must preserve its confirmed projectile base damage");
        helper.assertTrue(projectile.isSilent(),
                "Matchlock Pistol must preserve the native silent projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Matchlock Pistol must preserve native projectile knockback");
        helper.assertTrue(projectile.isCritArrow(),
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 3.2D) < 1.0E-6D,
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
        helper.assertFalse(weapon.getOrCreateTag().contains(ArtilleryNativeState.LOADED_KEY),
                "Tiller Gun reload must not invent the optional loaded flag");

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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.0D) < 1.0E-6D,
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.2D) < 1.0E-6D,
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.2D) < 1.0E-6D,
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 5.25D) < 1.0E-6D,
                "Hackbut must preserve its confirmed projectile base damage");
        helper.assertFalse(projectile.isSilent(),
                "Hackbut must preserve the native audible projectile flag");
        helper.assertTrue(projectile.getKnockback() == 1,
                "Hackbut must preserve native projectile knockback");
        helper.assertTrue(projectile.getPierceLevel() == 1,
                "Hackbut must preserve native projectile piercing");
        helper.assertTrue(projectile.isCritArrow(),
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 1.85D) < 1.0E-6D,
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
        helper.assertTrue(weapon.getOrCreateTag().getBoolean(ArtilleryNativeState.LOADED_KEY),
                "Noble Handgonne reload must set the native loaded flag");
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 2.55D) < 1.0E-6D,
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
        // Checked before the first round lands, because a recruit may pick fired arrows back up.
        helper.runAtTickTime(ArtilleryAddonAdapter.INSTANCE.reloadTicks(weapon) + 5L, () ->
                helper.assertTrue(recruit.getInventory().countItem(ammo.getItem()) == 0,
                        "the Chu Ko Nu combat path must consume its whole magazine during one reload"));
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
        helper.assertTrue(Math.abs(projectile.getBaseDamage() - 1.85D) < 1.0E-6D,
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
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack(SupportedArtillery.ARQUEBUS_ID));
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
                "the recruit must hold the firearm, it holds " + recruit.getMainHandItem());
        helper.assertTrue(BoomstickCarryOrder.isCarrying(recruit),
                "the carry flag must be set so the client can render the parade pose");
        helper.assertTrue(recruit.getInventory().countItem(stack("minecraft:iron_sword").getItem()) == 1,
                "the displaced melee weapon must be stored, not destroyed");
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
        helper.assertFalse(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getMainHandItem()),
                "the firearm must leave the main hand, it holds " + recruit.getMainHandItem());
        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:iron_sword").getItem()),
                "the recruit must take its melee weapon back, it holds " + recruit.getMainHandItem());
        helper.assertFalse(BoomstickCarryOrder.isCarrying(recruit),
                "the carry flag must be cleared with the stowed firearm");
        helper.assertTrue(
                recruit.getInventory().countItem(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()) == 1,
                "stowing must neither lose nor duplicate the firearm");
        helper.succeed();
    }

    /**
     * The order must reach a firearm sitting in the off-hand slot.
     *
     * <p>A player handing out weapons through the recruit's inventory screen can drop the gun into
     * either hand slot. Recruits' own {@code switchMainHandItem} starts its scan past both of them,
     * so a gun parked in the off hand would be invisible to the order.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderDrawsAFirearmOutOfTheOffHand(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, stack("minecraft:iron_sword"));
        recruit.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                stack(SupportedBoomsticks.ARQUEBUS_ID));

        boolean carrying = BoomstickCarryOrder.apply(recruit, true);

        helper.assertTrue(carrying, "the order must report the drawn firearm");
        helper.assertTrue(recruit.getMainHandItem().is(stack(SupportedBoomsticks.ARQUEBUS_ID).getItem()),
                "the firearm must move to the main hand, it holds " + recruit.getMainHandItem());
        helper.assertTrue(recruit.getOffhandItem().is(stack("minecraft:iron_sword").getItem()),
                "the displaced melee weapon must take the off hand, it holds " + recruit.getOffhandItem());
        helper.assertTrue(
                recruit.getInventory()
                        .getItem(recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND))
                        .is(stack("minecraft:iron_sword").getItem()),
                "the inventory slot backing the off hand must agree with the equipment slot");
        helper.succeed();
    }

    /**
     * A recruit whose only weapon is the firearm must keep holding it.
     *
     * <p>Stowing is an order about how a company carries its weapons. Emptying the hand of a recruit
     * that owns nothing else would disarm it outright, which is not what the order was asked to do.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carryOrderKeepsTheFirearmWhenThereIsNothingToTakeBack(GameTestHelper helper) {
        CrossBowmanEntity recruit = spawnCrossbowman(helper);
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ARQUEBUS_ID));
        BoomstickCarryOrder.apply(recruit, true);

        boolean carrying = BoomstickCarryOrder.apply(recruit, false);

        helper.assertTrue(carrying, "the recruit still carries the firearm, so the order must say so");
        helper.assertTrue(
                RecruitWeaponAdapters.production().isSupportedWeapon(recruit.getMainHandItem()),
                "the only weapon the recruit owns must stay in its hand, it holds "
                        + recruit.getMainHandItem());
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
        recruit.getInventory().addItem(stack("minecraft:crossbow"));
        recruit.getInventory().addItem(stack(SupportedBoomsticks.ARQUEBUS_ID));
        BoomstickCarryOrder.apply(recruit, true);

        BoomstickCarryOrder.apply(recruit, false);

        helper.assertTrue(recruit.getMainHandItem().is(stack("minecraft:crossbow").getItem()),
                "the recruit must fall back to its crossbow, it holds " + recruit.getMainHandItem());
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
                "the order must draw the gun, it drew " + recruit.getMainHandItem());
        helper.assertTrue(
                recruit.getInventory()
                        .getItem(recruit.getInventorySlotIndex(EquipmentSlot.MAINHAND))
                        .is(stack(SupportedArtillery.ARQUEBUS_ID).getItem()),
                "the inventory slot backing the main hand must show the same gun, it shows "
                        + recruit.getInventory().getItem(recruit.getInventorySlotIndex(EquipmentSlot.MAINHAND)));
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
