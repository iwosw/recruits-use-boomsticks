package org.iwoss.recruits_use_boomsticks.compat;

import com.magistuarmory.thirtyyearswar.item.GunItem;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.Optional;
import java.util.function.Predicate;

/** Optional typed boundary: only discovery loads this class when GunItem is installed. */
public final class ThirtyYearsWarAdapter implements BoomstickWeaponAdapter {
    public static final ThirtyYearsWarAdapter INSTANCE = new ThirtyYearsWarAdapter();
    private static final String RELOADING = RecruitsUseBoomsticks.MOD_ID + ":tyw_reloading";
    private static final String FIRING = RecruitsUseBoomsticks.MOD_ID + ":tyw_firing";

    private ThirtyYearsWarAdapter() {
    }

    @Override
    public RecruitWeaponIntegration integration() {
        return RecruitWeaponIntegration.THIRTY_YEARS_WAR;
    }

    @Override
    public boolean supports(ItemStack weapon) {
        return gun(weapon) != null;
    }

    @Override
    public boolean supportsAmmo(ItemStack ammo) {
        return ammo != null && !ammo.isEmpty()
                && SupportedThirtyYearsWar.isAmmoId(registryId(ammo));
    }

    @Override
    public boolean supportsProjectile(Class<?> projectileType) {
        // Native guns raycast synchronously; claiming Arrow would capture unrelated vanilla shots.
        return false;
    }

    @Override
    public Optional<BoomstickWeaponProfile> profile(ItemStack weapon) {
        GunItem gun = gun(weapon);
        if (gun == null || !gun.config.enabled || !Double.isFinite(gun.config.range)
                || gun.config.range <= 0 || !Double.isFinite(gun.config.spread)
                || gun.config.spread < 0 || gun.config.spread > Float.MAX_VALUE
                || !Float.isFinite(gun.config.damage) || gun.config.damage < 0) {
            return Optional.empty();
        }
        // The shared profile requires a positive speed; instant shots never use its flight estimate.
        return Optional.of(new BoomstickWeaponProfile(registryId(weapon),
                BoomstickAmmoType.PAPER_CARTRIDGE, 1, 1.0D,
                (float) gun.config.spread, BoomstickSound.NONE));
    }

    @Override
    public double estimatedVolleyDamage(ItemStack weapon) {
        GunItem gun = gun(weapon);
        return gun == null ? 0 : BoomstickDamagePolicy.configuredDamage(gun.config.damage, integration());
    }

    @Override
    public boolean isInstantShot() {
        return true;
    }

    @Override
    public double effectiveRange(ItemStack weapon, double maxRange) {
        GunItem gun = gun(weapon);
        return gun == null || profile(weapon).isEmpty() ? 0 : Math.min(maxRange, gun.config.range);
    }

    @Override
    public boolean isLoaded(ItemStack weapon) {
        return supports(weapon) && GunItem.isCharged(weapon);
    }

    @Override
    public void setLoaded(ItemStack weapon, boolean loaded) {
        if (supports(weapon)) {
            GunItem.setCharged(weapon, loaded);
        }
    }

    @Override
    public void setReloading(ItemStack weapon, boolean reloading) {
        setFlag(weapon, RELOADING, reloading);
    }

    @Override
    public boolean isReloading(ItemStack weapon) {
        return supports(weapon) && weapon.hasTag() && weapon.getTag().getBoolean(RELOADING);
    }

    @Override
    public void setFiring(ItemStack weapon, boolean firing) {
        setFlag(weapon, FIRING, firing);
    }

    @Override
    public int reloadTicks(ItemStack weapon) {
        GunItem gun = gun(weapon);
        return gun == null ? 0 : Mth.clamp(gun.getReloadTicks(), 1, 72000);
    }

    @Override
    public int cooldownTicks(ItemStack weapon) {
        GunItem gun = gun(weapon);
        return gun == null ? 0 : Math.max(0, gun.config.cooldownTicks);
    }

    @Override
    public boolean hasReloadComponents(CrossBowmanEntity recruit, ItemStack weapon) {
        GunItem gun = gun(weapon);
        return gun != null && (gun.mechanismType != GunItem.MechanismType.MATCHLOCK
                || findSlot(recruit, stack -> stack.is(Items.FLINT_AND_STEEL)) >= 0);
    }

    @Override
    public boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        // These are physical cartridges, independent of Recruits' vanilla-arrow exemption.
        return profile(weapon).isPresent() && (isLoaded(weapon)
                || hasReloadComponents(recruit, weapon) && findSlot(recruit, this::supportsAmmo) >= 0);
    }

    @Override
    public boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        if (recruit == null || recruit.level().isClientSide
                || !CompatConfig.isIntegrationEnabled(integration()) || profile(weapon).isEmpty()) {
            return false;
        }
        if (isLoaded(weapon)) {
            return true;
        }
        int ammoSlot = findSlot(recruit, this::supportsAmmo);
        GunItem gun = gun(weapon);
        int flintSlot = findSlot(recruit, stack -> stack.is(Items.FLINT_AND_STEEL));
        if (ammoSlot < 0 || gun.mechanismType == GunItem.MechanismType.MATCHLOCK && flintSlot < 0) {
            return false;
        }
        // NPC reload is atomic at completion: cancellation spends neither cartridge nor tool wear.
        recruit.getInventory().removeItem(ammoSlot, 1);
        if (gun.mechanismType == GunItem.MechanismType.MATCHLOCK) {
            ItemStack flint = recruit.getInventory().getItem(flintSlot);
            flint.hurtAndBreak(1, recruit, entity -> entity.broadcastBreakEvent(InteractionHand.OFF_HAND));
            recruit.getInventory().setItem(flintSlot, flint.isEmpty() ? ItemStack.EMPTY : flint);
        }
        recruit.getInventory().setChanged();
        return true;
    }

    @Override
    public ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition) {
        if (recruit == null || recruit.level().isClientSide) {
            return new ShotResult(ShotOutcome.CLIENT_SIDE_REJECTED, 0);
        }
        GunItem gun = gun(weapon);
        if (gun == null || !CompatConfig.isIntegrationEnabled(integration()) || profile(weapon).isEmpty()) {
            return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
        }
        if (!isLoaded(weapon)) {
            return new ShotResult(ShotOutcome.NOT_LOADED, 0);
        }
        if (targetPosition == null || !Double.isFinite(targetPosition.x)
                || !Double.isFinite(targetPosition.y) || !Double.isFinite(targetPosition.z)) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }
        Vec3 direction = targetPosition.subtract(recruit.getEyePosition());
        if (direction.lengthSqr() < 1.0E-8D || direction.lengthSqr() > gun.config.range * gun.config.range) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }
        float yaw = recruit.getYRot();
        float pitch = recruit.getXRot();
        recruit.setYRot((float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F);
        recruit.setXRot((float) (-Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG));
        // A hitscan may already have dealt damage before an effect listener throws. Spend first;
        // rolling back this payload could let the same cartridge deal damage twice.
        setLoaded(weapon, false);
        try {
            gun.performShooting(weapon, recruit.level(), recruit);
            weapon.hurtAndBreak(1, recruit, entity -> entity.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            setFiring(weapon, true);
            return new ShotResult(ShotOutcome.FIRED, 0);
        } catch (RuntimeException | LinkageError exception) {
            RecruitsUseBoomsticks.LOGGER.error("Thirty Years War shot failed for recruit {} and {}",
                    recruit.getUUID(), registryId(weapon), exception);
            return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
        } finally {
            recruit.setYRot(yaw);
            recruit.setXRot(pitch);
        }
    }

    private void setFlag(ItemStack weapon, String key, boolean value) {
        if (supports(weapon)) {
            if (value) {
                weapon.getOrCreateTag().putBoolean(key, true);
            } else if (weapon.hasTag()) {
                weapon.getTag().remove(key);
            }
        }
    }

    private static GunItem gun(ItemStack weapon) {
        return weapon != null && !weapon.isEmpty() && weapon.getItem() instanceof GunItem gun
                && SupportedThirtyYearsWar.isWeaponId(registryId(weapon)) ? gun : null;
    }

    private static String registryId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static int findSlot(CrossBowmanEntity recruit, Predicate<ItemStack> predicate) {
        if (recruit == null || recruit.getInventory() == null) {
            return -1;
        }
        for (int slot = 0; slot < recruit.getInventory().getContainerSize(); slot++) {
            EquipmentSlot equipment = recruit.getEquipmentSlotIndex(slot);
            if ((equipment == null || equipment == EquipmentSlot.OFFHAND)
                    && predicate.test(recruit.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }
}
