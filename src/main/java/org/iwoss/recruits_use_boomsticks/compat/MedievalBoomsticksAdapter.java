package org.iwoss.recruits_use_boomsticks.compat;

import com.TBK.medieval_boomsticks.Config;
import com.TBK.medieval_boomsticks.common.items.RechargeItem;
import com.TBK.medieval_boomsticks.common.registers.MBSounds;
import com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickCombatPolicy;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Medieval Boomsticks implementation of the server-safe weapon adapter contract. */
public final class MedievalBoomsticksAdapter implements BoomstickWeaponAdapter {
    public static final MedievalBoomsticksAdapter INSTANCE = new MedievalBoomsticksAdapter();

    private MedievalBoomsticksAdapter() {
    }

    @Override
    public boolean supports(ItemStack weapon) {
        return profile(weapon).isPresent();
    }

    @Override
    public Optional<BoomstickWeaponProfile> profile(ItemStack weapon) {
        return SupportedBoomsticks.profileFor(weapon);
    }

    @Override
    public boolean isLoaded(ItemStack weapon) {
        Optional<BoomstickWeaponProfile> profileResult = profile(weapon);
        if (profileResult.isEmpty() || !RechargeItem.isCharged(weapon)) {
            return false;
        }
        BoomstickWeaponProfile profile = profileResult.orElseThrow();
        try {
            if (hasValidChargedProjectiles(weapon, profile)) {
                return true;
            }
            CompoundTag tag = weapon.getTag();
            if (tag == null || !tag.contains("ChargedProjectiles")) {
                weapon.getOrCreateTag().put("ChargedProjectiles", createNativeChargedProjectiles(profile));
                RecruitsUseBoomsticks.LOGGER.info("Normalized legacy loaded state for {}", profile.registryId());
                return true;
            }
            clearInvalidLoadedState(weapon, profile, null);
            return false;
        } catch (RuntimeException exception) {
            clearInvalidLoadedState(weapon, profile, exception);
            return false;
        }
    }

    @Override
    public void setLoaded(ItemStack weapon, boolean loaded) {
        Optional<BoomstickWeaponProfile> profileResult = profile(weapon);
        if (profileResult.isEmpty()) {
            return;
        }
        CompoundTag tag = weapon.getOrCreateTag();
        if (loaded) {
            tag.put("ChargedProjectiles", createNativeChargedProjectiles(profileResult.orElseThrow()));
        } else {
            tag.remove("ChargedProjectiles");
        }
        RechargeItem.setCharged(weapon, loaded);
    }

    @Override
    public void setReloading(ItemStack weapon, boolean reloading) {
        if (supports(weapon)) {
            RechargeItem.setReCharge(weapon, reloading);
        }
    }

    @Override
    public boolean isReloading(ItemStack weapon) {
        return supports(weapon) && RechargeItem.isReCharge(weapon);
    }

    @Override
    public void setFiring(ItemStack weapon, boolean firing) {
        if (supports(weapon)) {
            RechargeItem.setFire(weapon, firing);
        }
    }

    @Override
    public int reloadTicks(ItemStack weapon) {
        return profile(weapon)
                .map(profile -> Math.max(1, RechargeItem.getChargeDuration(weapon)))
                .orElse(0);
    }

    @Override
    public int cooldownTicks(ItemStack weapon) {
        return profile(weapon)
                .map(profile -> BoomstickCombatPolicy.cooldownTicks())
                .orElse(0);
    }

    @Override
    public boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        return profile(weapon)
                .map(profile -> BoomstickAmmoAccess.hasAmmo(recruit, profile, ammoRequired))
                .orElse(false);
    }

    @Override
    public boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        return profile(weapon)
                .map(profile -> BoomstickAmmoAccess.consumeAmmo(recruit, profile, ammoRequired))
                .orElse(false);
    }

    @Override
    public ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition) {
        if (recruit == null || !recruit.isAlive() || weapon == null || weapon.isEmpty()) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }
        if (!(recruit.level() instanceof ServerLevel serverLevel)) {
            return new ShotResult(
                    recruit.level().isClientSide ? ShotOutcome.CLIENT_SIDE_REJECTED : ShotOutcome.INVALID_TARGET,
                    0
            );
        }
        if (targetPosition == null || !isFinite(targetPosition)) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }

        List<AbstractArrow> projectiles = new ArrayList<>();
        List<Boolean> misfires = new ArrayList<>();
        ItemStack originalWeapon = weapon.copy();
        try {
            Optional<BoomstickWeaponProfile> profileResult = profile(weapon);
            if (profileResult.isEmpty()) {
                return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
            }
            BoomstickWeaponProfile profile = profileResult.orElseThrow();

            Vec3 origin = recruit.getEyePosition(1.0F);
            Vec3 direction = projectileDirection(origin, targetPosition, profile);
            if (direction.lengthSqr() < 1.0E-8D) {
                return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
            }
            direction = direction.normalize();
            if (!isLoaded(weapon)) {
                return new ShotResult(ShotOutcome.NOT_LOADED, 0);
            }

            boolean firearm = RechargeItem.isFireGun(weapon);
            for (int index = 0; index < profile.projectileCount(); index++) {
                boolean misfire = BoomstickCombatPolicy.isMisfire(
                        firearm,
                        serverLevel.random.nextFloat(),
                        Config.probabilityFail);
                AbstractArrow projectile = createProjectile(serverLevel, recruit, weapon, profile);
                if (misfire) {
                    projectile.setPos(
                            origin.x + direction.x * 0.1D,
                            origin.y - 0.2D,
                            origin.z + direction.z * 0.1D);
                    projectile.setDeltaMovement(0.0D, -1.0D, 0.0D);
                } else {
                    projectile.setPos(origin.x, origin.y, origin.z);
                    Vec3 projectileDirection = directionForProjectile(direction, profile, index);
                    projectile.shoot(
                            projectileDirection.x,
                            projectileDirection.y,
                            projectileDirection.z,
                            (float) profile.projectileVelocity(),
                            profile.inaccuracy()
                    );
                }
                projectiles.add(projectile);
                if (!serverLevel.addFreshEntity(projectile)) {
                    rollback(projectiles);
                    restoreStack(weapon, originalWeapon);
                    return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
                }
                misfires.add(misfire);
            }

            for (boolean misfire : misfires) {
                weapon.hurtAndBreak(1, recruit, ignored -> recruit.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                playShotEffectsSafely(serverLevel, recruit, profile, origin, misfire, firearm);
            }
            setLoaded(weapon, false);
            setFiring(weapon, true);
            return new ShotResult(ShotOutcome.FIRED, projectiles.size());
        } catch (RuntimeException exception) {
            rollback(projectiles);
            restoreStack(weapon, originalWeapon);
            RecruitsUseBoomsticks.LOGGER.error(
                    "Boomsticks adapter failed for recruit {} (entity {}) with weapon {}",
                    recruit.getStringUUID(),
                    recruit.getId(),
                    BuiltInRegistries.ITEM.getKey(weapon.getItem()),
                    exception
            );
            return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
        }
    }

    private static AbstractArrow createProjectile(
            Level level,
            CrossBowmanEntity recruit,
            ItemStack weapon,
            BoomstickWeaponProfile profile
    ) {
        AbstractArrow projectile = profile.ammoType() == BoomstickAmmoType.HEAVY_BOLT
                ? new HeavyBoltProjectile(level, recruit, weapon)
                : new RoundBallProjectile(level, recruit, weapon);
        projectile.setOwner(recruit);
        projectile.pickup = profile.ammoType() == BoomstickAmmoType.HEAVY_BOLT
                ? AbstractArrow.Pickup.ALLOWED
                : AbstractArrow.Pickup.DISALLOWED;
        return projectile;
    }

    private static ListTag createNativeChargedProjectiles(BoomstickWeaponProfile profile) {
        ItemStack ammo = expectedAmmo(profile);

        ListTag chargedProjectiles = new ListTag();
        for (int index = 0; index < profile.projectileCount(); index++) {
            chargedProjectiles.add(ammo.save(new CompoundTag()));
        }
        return chargedProjectiles;
    }

    private static boolean hasValidChargedProjectiles(ItemStack weapon, BoomstickWeaponProfile profile) {
        CompoundTag tag = weapon.getTag();
        if (tag == null || !tag.contains("ChargedProjectiles", Tag.TAG_LIST)) {
            return false;
        }
        ListTag payload = tag.getList("ChargedProjectiles", Tag.TAG_COMPOUND);
        if (payload.size() != profile.projectileCount()) {
            return false;
        }
        ItemStack expected = expectedAmmo(profile);
        for (int index = 0; index < payload.size(); index++) {
            ItemStack projectile = ItemStack.of(payload.getCompound(index));
            if (projectile.isEmpty()
                    || projectile.getItem() != expected.getItem()
                    || projectile.getCount() != 1) {
                return false;
            }
        }
        return true;
    }

    private static void clearInvalidLoadedState(
            ItemStack weapon,
            BoomstickWeaponProfile profile,
            RuntimeException exception
    ) {
        weapon.getOrCreateTag().remove("ChargedProjectiles");
        RechargeItem.setCharged(weapon, false);
        if (exception == null) {
            RecruitsUseBoomsticks.LOGGER.warn("Cleared invalid loaded payload for {}", profile.registryId());
        } else {
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Cleared invalid loaded payload for {} after validation failed",
                    profile.registryId(),
                    exception);
        }
    }

    private static ItemStack expectedAmmo(BoomstickWeaponProfile profile) {
        String ammoId = profile.ammoType() == BoomstickAmmoType.HEAVY_BOLT
                ? SupportedBoomsticks.HEAVY_BOLT_ID
                : SupportedBoomsticks.ROUND_BALL_ID;
        ResourceLocation id = ResourceLocation.tryParse(ammoId);
        if (id == null) {
            throw new IllegalStateException("invalid supported ammo ID " + ammoId);
        }
        ItemStack ammo = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (ammo.isEmpty()) {
            throw new IllegalStateException("missing supported ammo " + ammoId);
        }
        return ammo;
    }

    private static Vec3 directionForProjectile(
            Vec3 direction,
            BoomstickWeaponProfile profile,
            int projectileIndex
    ) {
        if (!SupportedBoomsticks.SPIKED_HANDGONNE_ID.equals(profile.registryId())
                || profile.projectileCount() != 3) {
            return direction;
        }
        float degrees = (projectileIndex - 1) * 10.0F;
        return direction.yRot((float) Math.toRadians(degrees)).normalize();
    }

    private static Vec3 projectileDirection(
            Vec3 origin,
            Vec3 targetPosition,
            BoomstickWeaponProfile profile
    ) {
        Vec3 direct = targetPosition.subtract(origin);
        if (!SupportedBoomsticks.ARBALEST_ID.equals(profile.registryId())) {
            return direct;
        }
        double horizontalDistance = Math.sqrt(direct.x * direct.x + direct.z * direct.z);
        return new Vec3(direct.x, direct.y + horizontalDistance * 0.2D, direct.z);
    }

    private static void rollback(List<AbstractArrow> projectiles) {
        for (AbstractArrow projectile : projectiles) {
            projectile.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    private static void restoreStack(ItemStack target, ItemStack snapshot) {
        target.setCount(snapshot.getCount());
        target.setTag(snapshot.hasTag() ? snapshot.getTag().copy() : null);
    }

    private static void playShotEffects(
            ServerLevel level,
            CrossBowmanEntity recruit,
            BoomstickWeaponProfile profile,
            Vec3 origin,
            boolean misfire,
            boolean firearm
    ) {
        SoundEvent sound = misfire ? SoundEvents.GENERIC_EXTINGUISH_FIRE : soundFor(profile.firingSound());
        level.playSound(
                null,
                recruit.getX(),
                recruit.getY(),
                recruit.getZ(),
                sound,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        if (CompatConfig.SMOKE_PARTICLES.get() && firearm) {
            level.sendParticles(
                    ParticleTypes.SMOKE,
                    origin.x,
                    origin.y,
                    origin.z,
                    5,
                    0.08D,
                    0.08D,
                    0.08D,
                    0.02D
            );
        }
    }

    private static void playShotEffectsSafely(
            ServerLevel level,
            CrossBowmanEntity recruit,
            BoomstickWeaponProfile profile,
            Vec3 origin,
            boolean allMisfired,
            boolean firearm
    ) {
        try {
            playShotEffects(level, recruit, profile, origin, allMisfired, firearm);
        } catch (RuntimeException exception) {
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Boomsticks shot effects failed for recruit {} with weapon {}",
                    recruit.getStringUUID(),
                    profile.registryId(),
                    exception);
        }
    }

    private static SoundEvent soundFor(BoomstickSound sound) {
        return switch (sound) {
            case HANDGONNE_SHOOT -> MBSounds.HANDGONNE_SHOOT.get();
            case ARQUEBUS_SHOOT -> MBSounds.ARQUEBUS_SHOOT.get();
            case CROSSBOW_SHOOT -> SoundEvents.CROSSBOW_SHOOT;
        };
    }


    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
