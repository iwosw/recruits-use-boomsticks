package org.iwoss.recruits_use_boomsticks.compat;

import com.TBK.medieval_boomsticks.Config;
import com.TBK.medieval_boomsticks.common.items.RechargeItem;
import com.TBK.medieval_boomsticks.common.registers.MBSounds;
import com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
        return supports(weapon) && RechargeItem.isCharged(weapon);
    }

    @Override
    public void setLoaded(ItemStack weapon, boolean loaded) {
        if (supports(weapon)) {
            RechargeItem.setCharged(weapon, loaded);
        }
    }

    @Override
    public void setReloading(ItemStack weapon, boolean reloading) {
        if (supports(weapon)) {
            RechargeItem.setReCharge(weapon, reloading);
        }
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

        try {
            Optional<BoomstickWeaponProfile> profileResult = profile(weapon);
            if (profileResult.isEmpty()) {
                return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
            }
            BoomstickWeaponProfile profile = profileResult.orElseThrow();
            if (!isLoaded(weapon)) {
                return new ShotResult(ShotOutcome.NOT_LOADED, 0);
            }

            Vec3 origin = recruit.getEyePosition(1.0F);
            Vec3 direction = targetPosition.subtract(origin);
            if (direction.lengthSqr() < 1.0E-8D) {
                return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
            }
            direction = direction.normalize();

            boolean firearm = RechargeItem.isFireGun(weapon);
            boolean misfire = firearm
                    && serverLevel.random.nextFloat() < Config.probabilityFail / 100.0F;
            List<AbstractArrow> projectiles = new ArrayList<>(profile.projectileCount());
            for (int index = 0; index < profile.projectileCount(); index++) {
                AbstractArrow projectile = createProjectile(serverLevel, recruit, weapon, profile);
                projectile.setPos(origin.x, origin.y, origin.z);
                if (misfire) {
                    projectile.setDeltaMovement(Vec3.ZERO);
                } else {
                    Vec3 projectileDirection = directionForProjectile(direction, profile, index);
                    projectile.shoot(
                            projectileDirection.x,
                            projectileDirection.y,
                            projectileDirection.z,
                            (float) profile.projectileVelocity(),
                            profile.inaccuracy()
                    );
                }
                if (!serverLevel.addFreshEntity(projectile)) {
                    rollback(projectiles);
                    return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
                }
                projectiles.add(projectile);
            }

            weapon.hurtAndBreak(1, recruit, ignored -> recruit.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            setLoaded(weapon, false);
            setFiring(weapon, true);
            playShotEffects(serverLevel, recruit, profile, origin, misfire, firearm);
            return new ShotResult(ShotOutcome.FIRED, projectiles.size());
        } catch (RuntimeException exception) {
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

    private static void rollback(List<AbstractArrow> projectiles) {
        for (AbstractArrow projectile : projectiles) {
            projectile.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
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
