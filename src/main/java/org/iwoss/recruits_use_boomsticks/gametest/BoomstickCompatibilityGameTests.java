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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickAttackState;
import org.iwoss.recruits_use_boomsticks.ai.RecruitBoomstickAttackGoal;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickAmmoAccess;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.MedievalBoomsticksAdapter;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

@GameTestHolder(RecruitsUseBoomsticks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoomstickCompatibilityGameTests {
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
