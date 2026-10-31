package org.iwoss.recruits_use_boomsticks.gametest;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.ai.RecruitBoomstickAttackGoal;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickTransientStateRecovery;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponIntegration;
import org.iwoss.recruits_use_boomsticks.compat.SupportedThirtyYearsWar;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.iwoss.recruits_use_boomsticks.event.ThirtyYearsWarShotEvents;

import java.util.Optional;

/** No upstream type references: the same class also verifies installations without the addon. */
@GameTestHolder(RecruitsUseBoomsticks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThirtyYearsWarGameTests {
    private ThirtyYearsWarGameTests() {
    }

    @GameTest(template = "empty")
    public static void optionalDependencyMatchesRuntimeProfile(GameTestHelper helper) {
        boolean installed = ModList.get().isLoaded(SupportedThirtyYearsWar.MOD_ID);
        helper.assertTrue(installed == Boolean.getBoolean("recruits_use_boomsticks.expectThirtyYearsWar"),
                "Thirty Years War installation must match the selected Gradle runtime profile");
        for (String id : SupportedThirtyYearsWar.WEAPON_IDS) {
            boolean registered = BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(id));
            helper.assertTrue(registered == installed, "native firearm registration must match mod presence");
            if (installed) {
                helper.assertTrue(adapter(stack(id)).integration() == RecruitWeaponIntegration.THIRTY_YEARS_WAR,
                        "each native firearm must resolve to exactly one adapter");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void matchlockLoadsPhysicalCartridgeAndFiresNativeRay(GameTestHelper helper) {
        loadingAndShot(helper, SupportedThirtyYearsWar.MATCHLOCK_MUSKET_ID, true);
    }

    @GameTest(template = "empty")
    public static void wheellockLoadsWithoutFlintAndFiresNativeRay(GameTestHelper helper) {
        loadingAndShot(helper, SupportedThirtyYearsWar.WHEELLOCK_PISTOL_ID, false);
    }

    private static void loadingAndShot(GameTestHelper helper, String id, boolean matchlock) {
        if (absent(helper)) return;
        CrossBowmanEntity recruit = recruit(helper, 1);
        ItemStack weapon = stack(id);
        BoomstickWeaponAdapter adapter = adapter(weapon);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        helper.assertTrue(recruit.wantsToPickUp(weapon) && recruit.wantsToPickUp(cartridge(1)),
                "recruit pickup must recognize native firearm and paper cartridges");
        recruit.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        recruit.getInventory().setItem(6, cartridge(2));
        recruit.getInventory().setItem(7, new ItemStack(Items.FLINT_AND_STEEL));
        ItemStack flint = recruit.getInventory().getItem(7);
        if (matchlock) {
            recruit.getInventory().setItem(7, ItemStack.EMPTY);
            helper.assertFalse(adapter.hasAmmo(recruit, weapon, false), "matchlock must refuse missing flint");
            helper.assertFalse(adapter.consumeAmmo(recruit, weapon, false), "missing tool must spend nothing");
            helper.assertTrue(recruit.getInventory().getItem(6).getCount() == 2, "failed reload must keep ammo");
            recruit.getInventory().setItem(7, flint);
        } else {
            recruit.getInventory().setItem(7, ItemStack.EMPTY);
        }

        RecruitBoomstickAttackGoal reload = RecruitBoomstickAttackGoal.passiveReload(recruit);
        helper.assertTrue(reload.canUse(), "supported gun must start passive reload");
        reload.start();
        for (int tick = 0; tick < adapter.reloadTicks(weapon) / 2; tick++) reload.tick();
        reload.stop();
        helper.assertFalse(adapter.isLoaded(weapon), "interrupted reload must not load a free cartridge");
        helper.assertTrue(recruit.getInventory().getItem(6).getCount() == 2 && flint.getDamageValue() == 0,
                "interruption must preserve both physical ammo and tool wear");
        reload.start();
        for (int tick = 0; tick <= adapter.reloadTicks(weapon) + 2; tick++) reload.tick();
        reload.stop();
        helper.assertTrue(adapter.isLoaded(weapon) && weapon.getTag().getBoolean("Charged"),
                "AI must commit the native Charged flag");
        helper.assertTrue(recruit.getInventory().getItem(6).getCount() == 1,
                "completed reload must spend exactly one physical cartridge");
        helper.assertTrue(flint.getDamageValue() == (matchlock ? 1 : 0), "only matchlock wears the real flint");
        helper.assertTrue(recruit.getOffhandItem().is(Items.SHIELD), "reload must preserve occupied off hand");
        ItemStack saved = ItemStack.of(weapon.save(new CompoundTag()));
        recruit.setItemSlot(EquipmentSlot.MAINHAND, saved);
        adapter.setReloading(saved, true);
        BoomstickTransientStateRecovery.recover(recruit);
        helper.assertTrue(adapter.isLoaded(saved) && !adapter.isReloading(saved),
                "save/load recovery must clear animation without deleting the paid charge");
        LivingEntity target = target(helper, 5);
        float health = target.getHealth();
        var result = adapter.fire(recruit, saved, target.getBoundingBox().getCenter());
        helper.assertTrue(result.fired() && result.projectilesSpawned() == 0, "native shot must be instant");
        helper.assertTrue(target.getHealth() < health, "native ray must damage the aimed-at target");
        helper.assertFalse(adapter.isLoaded(saved), "native charge must be cleared after the shot");
        helper.assertTrue(saved.getDamageValue() == 1, "NPC shot must wear the weapon once");
        helper.assertTrue(adapter.fire(recruit, saved, target.position()).outcome()
                == BoomstickWeaponAdapter.ShotOutcome.NOT_LOADED, "spent cartridge cannot fire twice");
        helper.assertFalse(adapter.supportsProjectile(AbstractArrow.class), "hitscan must never claim vanilla arrows");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paperCartridgeRequiredEvenWhenVanillaArrowsAreFree(GameTestHelper helper) {
        if (absent(helper)) return;
        CrossBowmanEntity recruit = recruit(helper, 1);
        ItemStack weapon = stack(SupportedThirtyYearsWar.WHEELLOCK_PISTOL_ID);
        BoomstickWeaponAdapter adapter = adapter(weapon);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().setItem(6, new ItemStack(Items.ARROW, 64));
        helper.assertFalse(adapter.hasAmmo(recruit, weapon, false), "free vanilla arrows cannot replace cartridges");
        helper.assertFalse(adapter.consumeAmmo(recruit, weapon, false), "wrong ammo must not load");
        recruit.getInventory().setItem(6, cartridge(1));
        helper.assertTrue(RecruitWeaponAdapters.production().findAmmo(recruit.getInventory().getItem(6))
                .orElseThrow() == adapter, "cartridge must have one unique owning adapter");
        helper.assertTrue(adapter.consumeAmmo(recruit, weapon, false), "physical cartridge must load");
        helper.assertTrue(recruit.getInventory().getItem(6).isEmpty(), "last cartridge must be consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedAimPreservesLoadedPayloadAndWeaponDurability(GameTestHelper helper) {
        if (absent(helper)) return;
        CrossBowmanEntity recruit = recruit(helper, 1);
        ItemStack weapon = stack(SupportedThirtyYearsWar.WHEELLOCK_PISTOL_ID);
        BoomstickWeaponAdapter adapter = adapter(weapon);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        adapter.setLoaded(weapon, true);
        for (Vec3 aim : new Vec3[]{null, new Vec3(Double.NaN, 0, 0), recruit.getEyePosition(),
                recruit.getEyePosition().add(1000, 0, 0)}) {
            helper.assertTrue(adapter.fire(recruit, weapon, aim).outcome()
                    == BoomstickWeaponAdapter.ShotOutcome.INVALID_TARGET, "invalid aim must fail closed");
            helper.assertTrue(adapter.isLoaded(weapon) && weapon.getDamageValue() == 0,
                    "rejected shot must preserve paid native charge and firearm durability");
        }
        helper.assertTrue(adapter.hasAmmo(recruit, weapon, true), "preloaded gun needs no spare supplies");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeRayStopsAtSolidBlocks(GameTestHelper helper) {
        if (absent(helper)) return;
        CrossBowmanEntity recruit = recruit(helper, 1);
        LivingEntity target = target(helper, 5);
        ItemStack weapon = stack(SupportedThirtyYearsWar.MATCHLOCK_MUSKET_ID);
        BoomstickWeaponAdapter adapter = adapter(weapon);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        adapter.setLoaded(weapon, true);
        for (int y = 2; y <= 4; y++) {
            for (int z = 0; z <= 2; z++) helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
        }
        float health = target.getHealth();
        helper.assertTrue(adapter.fire(recruit, weapon, target.getBoundingBox().getCenter()).fired(),
                "blocked shot still spends its loaded cartridge");
        helper.assertTrue(target.getHealth() == health, "native ray must not shoot through the wall");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeHitsPreserveArmorPiercingAndRestoreArmor(GameTestHelper helper) {
        if (absent(helper)) return;
        CrossBowmanEntity recruit = recruit(helper, 1);
        LivingEntity target = target(helper, 5);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        ItemStack weapon = stack(SupportedThirtyYearsWar.MATCHLOCK_MUSKET_ID);
        BoomstickWeaponAdapter adapter = adapter(weapon);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        adapter.setLoaded(weapon, true);
        float before = target.getHealth();
        helper.assertTrue(adapter.fire(recruit, weapon, target.getBoundingBox().getCenter()).fired(), "shot must fire");
        float raw = (float) adapter.estimatedVolleyDamage(weapon);
        float expected = CombatRules.getDamageAfterAbsorb(raw, 10.0F, 0.0F);
        helper.assertTrue(Math.abs(before - target.getHealth() - expected) < 0.01,
                "musket must use native 50 percent armor penetration");
        helper.assertTrue(target.getArmorValue() == 20, "temporary armor modifier must be removed after damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hitscanProtectsAlliesAndOwnerWithoutChangingPlayerShots(GameTestHelper helper) {
        if (absent(helper)) return;
        CrossBowmanEntity recruit = recruit(helper, 1);
        CrossBowmanEntity ally = recruit(helper, 3);
        Player owner = helper.makeMockPlayer();
        recruit.setOwnerUUID(Optional.of(owner.getUUID()));
        ally.setOwnerUUID(Optional.of(owner.getUUID()));
        DamageSource source = bullet(helper, recruit);
        LivingAttackEvent alliedHit = new LivingAttackEvent(ally, source, 18);
        ThirtyYearsWarShotEvents.onAttack(alliedHit);
        helper.assertTrue(alliedHit.isCanceled(), "same-owner recruit must be protected");
        LivingAttackEvent ownerHit = new LivingAttackEvent(owner, source, 18);
        ThirtyYearsWarShotEvents.onAttack(ownerHit);
        helper.assertTrue(ownerHit.isCanceled(), "recruit owner must be protected");
        LivingAttackEvent playerHit = new LivingAttackEvent(ally, bullet(helper, owner), 18);
        ThirtyYearsWarShotEvents.onAttack(playerHit);
        helper.assertFalse(playerHit.isCanceled(), "native player bullets must remain unchanged");
        ItemStack weapon = stack(SupportedThirtyYearsWar.MATCHLOCK_MUSKET_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        BoomstickWeaponAdapter adapter = adapter(weapon);
        adapter.setLoaded(weapon, true);
        float before = ally.getHealth();
        helper.assertTrue(adapter.fire(recruit, weapon, ally.getBoundingBox().getCenter()).fired(), "ray must fire");
        helper.assertTrue(ally.getHealth() == before, "actual native ray must not injure allied recruit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void simultaneousNativeBulletsHonorHurtCooldownSetting(GameTestHelper helper) {
        if (absent(helper)) return;
        boolean previous = CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.get();
        try {
            CrossBowmanEntity recruit = recruit(helper, 1);
            LivingEntity target = target(helper, 5);
            ItemStack weapon = stack(SupportedThirtyYearsWar.MATCHLOCK_MUSKET_ID);
            BoomstickWeaponAdapter adapter = adapter(weapon);
            recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.set(true);
            for (int shot = 0; shot < 2; shot++) {
                adapter.setLoaded(weapon, true);
                helper.assertTrue(adapter.fire(recruit, weapon, target.getBoundingBox().getCenter()).fired(),
                        "preloaded native ray must fire");
            }
            float expected = (float) (100 - 2 * adapter.estimatedVolleyDamage(weapon));
            helper.assertTrue(Math.abs(target.getHealth() - expected) < 0.01,
                    "both same-tick native bullets must deal their configured damage");
            CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.set(false);
            adapter.setLoaded(weapon, true);
            helper.assertTrue(adapter.fire(recruit, weapon, target.getBoundingBox().getCenter()).fired(),
                    "cooldown-protected shot must still spend its charge");
            helper.assertTrue(Math.abs(target.getHealth() - expected) < 0.01,
                    "disabling the setting must retain native post-hit invulnerability");
        } finally {
            CompatConfig.PROJECTILES_IGNORE_HURT_COOLDOWN.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void damageSettingsAndKillSwitchApplyOnlyToRecruitBullets(GameTestHelper helper) {
        if (absent(helper)) return;
        boolean enabled = CompatConfig.THIRTY_YEARS_WAR_ENABLED.get();
        boolean global = CompatConfig.ENABLED.get();
        double multiplier = CompatConfig.THIRTY_YEARS_WAR_DAMAGE_MULTIPLIER.get();
        try {
            CrossBowmanEntity recruit = recruit(helper, 1);
            LivingEntity target = target(helper, 5);
            ItemStack weapon = stack(SupportedThirtyYearsWar.WHEELLOCK_PISTOL_ID);
            BoomstickWeaponAdapter adapter = adapter(weapon);
            CompatConfig.THIRTY_YEARS_WAR_DAMAGE_MULTIPLIER.set(2.0D);
            LivingHurtEvent event = new LivingHurtEvent(target, bullet(helper, recruit), 18);
            ThirtyYearsWarShotEvents.onHurt(event);
            helper.assertTrue(event.getAmount() == 36, "local damage multiplier must apply once to recruit ray");
            LivingHurtEvent player = new LivingHurtEvent(target, bullet(helper, helper.makeMockPlayer()), 18);
            ThirtyYearsWarShotEvents.onHurt(player);
            helper.assertTrue(player.getAmount() == 18, "player damage must remain native");
            adapter.setLoaded(weapon, true);
            for (boolean localSwitch : new boolean[]{true, false}) {
                CompatConfig.THIRTY_YEARS_WAR_ENABLED.set(!localSwitch);
                CompatConfig.ENABLED.set(localSwitch);
                helper.assertTrue(RecruitWeaponAdapters.production().findEnabled(weapon).isEmpty(),
                        "global and local kill switches must suppress weapon routing");
                helper.assertTrue(adapter.fire(recruit, weapon, target.position()).outcome()
                        == BoomstickWeaponAdapter.ShotOutcome.INVALID_WEAPON, "disabled integration must refuse firing");
                helper.assertTrue(adapter.isLoaded(weapon), "disabled shot must keep its paid charge");
            }
        } finally {
            CompatConfig.THIRTY_YEARS_WAR_ENABLED.set(enabled);
            CompatConfig.ENABLED.set(global);
            CompatConfig.THIRTY_YEARS_WAR_DAMAGE_MULTIPLIER.set(multiplier);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "thirtyYearsWarCombat")
    public static void recruitCombatReloadsAndFiresRepeatedly(GameTestHelper helper) {
        if (absent(helper)) return;
        CrossBowmanEntity recruit = recruit(helper, 1);
        LivingEntity target = target(helper, 5);
        ItemStack weapon = stack(SupportedThirtyYearsWar.MATCHLOCK_MUSKET_ID);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        recruit.getInventory().setItem(6, cartridge(4));
        recruit.getInventory().setItem(7, new ItemStack(Items.FLINT_AND_STEEL));
        recruit.setTarget(target);
        RecruitBoomstickAttackGoal goal = new RecruitBoomstickAttackGoal(recruit, 1.0D);
        helper.assertTrue(goal.canUse(), "shared combat AI must accept native musket");
        goal.start();
        int[] observedShots = {0};
        Vec3 targetPosition = target.position();
        helper.onEachTick(() -> {
            // Other GameTests leave live mobs nearby. Reassert the controlled target before each
            // manual goal tick, and inspect damage synchronously at the shot boundary.
            target.setPos(targetPosition);
            target.setHealth(100);
            target.invulnerableTime = 0;
            recruit.setTarget(target);
            goal.tick();
            if (weapon.getDamageValue() > observedShots[0]) {
                observedShots[0] = weapon.getDamageValue();
                helper.assertTrue(target.getHealth() < 100 && target.getLastHurtByMob() == recruit,
                        "each combat AI shot must damage its assigned target as the recruit");
            }
            if (weapon.getDamageValue() >= 2) {
                goal.stop();
                helper.assertTrue(recruit.getInventory().getItem(6).getCount() == 2,
                        "two completed reloads must spend two cartridges");
                helper.succeed();
            }
        });
    }

    private static boolean absent(GameTestHelper helper) {
        if (!ModList.get().isLoaded(SupportedThirtyYearsWar.MOD_ID)) {
            helper.succeed();
            return true;
        }
        return false;
    }

    private static BoomstickWeaponAdapter adapter(ItemStack weapon) {
        return RecruitWeaponAdapters.production().find(weapon).orElseThrow();
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id)));
    }

    private static ItemStack cartridge(int count) {
        ItemStack stack = stack(SupportedThirtyYearsWar.PAPER_CARTRIDGE_ID);
        stack.setCount(count);
        return stack;
    }

    @SuppressWarnings("unchecked")
    private static CrossBowmanEntity recruit(GameTestHelper helper, int x) {
        var type = (EntityType<CrossBowmanEntity>) ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.tryParse("recruits:crossbowman"));
        CrossBowmanEntity recruit = helper.spawnWithNoFreeWill(type, x, 2, 1);
        recruit.setNoGravity(true);
        recruit.setShouldRanged(true);
        return recruit;
    }

    private static LivingEntity target(GameTestHelper helper, int x) {
        LivingEntity target = helper.spawnWithNoFreeWill(EntityType.HUSK, x, 2, 1);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(100);
        return target;
    }

    private static DamageSource bullet(GameTestHelper helper, LivingEntity shooter) {
        return new DamageSource(helper.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ThirtyYearsWarShotEvents.BULLET), shooter);
    }
}
