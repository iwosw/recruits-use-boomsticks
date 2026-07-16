package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Server-safe Artillery Addon boundary. It uses registry identities and the native projectile
 * registry, never the addon's player-only item/procedure/network entry points.
 */
public final class ArtilleryAddonAdapter implements BoomstickWeaponAdapter {
    public static final ArtilleryAddonAdapter INSTANCE = new ArtilleryAddonAdapter(
            () -> ModList.get().isLoaded(SupportedArtillery.MOD_ID));

    private static final String IRONBALL_PROJECTILE_CLASS =
            "net.mcreator.artilleryaddon.entity.IronballProjectileEntity";
    private static final String ARQUEBUS_FIRE_SOUND = SupportedArtillery.MOD_ID + ":arquebus_firing";

    private final BooleanSupplier availability;

    public ArtilleryAddonAdapter(BooleanSupplier availability) {
        this.availability = java.util.Objects.requireNonNull(availability, "availability");
    }

    public boolean isAvailable() {
        try {
            return availability.getAsBoolean();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public RecruitWeaponIntegration integration() {
        return RecruitWeaponIntegration.ARTILLERY_ADDON;
    }

    @Override
    public boolean supports(ItemStack weapon) {
        return isAvailable() && profile(weapon).isPresent();
    }

    @Override
    public boolean supportsAmmo(ItemStack ammo) {
        return isAvailable() && SupportedArtillery.IRON_BALL_ID.equals(registryId(ammo));
    }

    @Override
    public boolean supportsProjectile(Class<?> projectileType) {
        if (!isAvailable() || projectileType == null) {
            return false;
        }
        return hasNamedSuperclass(projectileType, IRONBALL_PROJECTILE_CLASS);
    }

    public boolean supportsProjectileClassName(String className) {
        return isAvailable() && IRONBALL_PROJECTILE_CLASS.equals(className);
    }

    @Override
    public Optional<BoomstickWeaponProfile> profile(ItemStack weapon) {
        String weaponId = registryId(weapon);
        if (!isAvailable() || !SupportedArtillery.isFirstSliceWeapon(weaponId)) {
            return Optional.empty();
        }
        return SupportedArtillery.profileFor(weaponId)
                .map(ArtilleryWeaponProfile::toBoomstickProfile);
    }

    private Optional<ArtilleryWeaponProfile> artilleryProfile(ItemStack weapon) {
        String weaponId = registryId(weapon);
        if (!isAvailable() || !SupportedArtillery.isFirstSliceWeapon(weaponId)) {
            return Optional.empty();
        }
        return SupportedArtillery.profileFor(weaponId);
    }

    @Override
    public boolean isLoaded(ItemStack weapon) {
        return artilleryProfile(weapon)
                .map(profile -> ArtilleryNativeState.isLoaded(weapon, profile))
                .orElse(false);
    }

    @Override
    public void setLoaded(ItemStack weapon, boolean loaded) {
        artilleryProfile(weapon).ifPresent(profile -> {
            if (loaded) {
                ArtilleryNativeState.markLoaded(weapon, profile);
            } else {
                ArtilleryNativeState.markFired(weapon, profile);
            }
        });
    }

    @Override
    public void setReloading(ItemStack weapon, boolean reloading) {
        if (artilleryProfile(weapon).isPresent()) {
            ArtilleryNativeState.setReloading(weapon, reloading);
        }
    }

    @Override
    public boolean isReloading(ItemStack weapon) {
        return artilleryProfile(weapon).isPresent() && ArtilleryNativeState.isReloading(weapon);
    }

    @Override
    public void setFiring(ItemStack weapon, boolean firing) {
        if (artilleryProfile(weapon).isPresent()) {
            ArtilleryNativeState.setFiring(weapon, firing);
        }
    }

    @Override
    public int reloadTicks(ItemStack weapon) {
        return artilleryProfile(weapon).map(ArtilleryWeaponProfile::reloadTicks).orElse(0);
    }

    @Override
    public int cooldownTicks(ItemStack weapon) {
        return artilleryProfile(weapon).map(ArtilleryWeaponProfile::cooldownTicks).orElse(0);
    }

    @Override
    public boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        if (recruit == null) {
            return false;
        }
        // The shared flag describes host/vanilla ammo policy; Artillery's iron ball is always physical ammo.
        return artilleryProfile(weapon)
                .map(profile -> ArtilleryAmmoAccess.count(recruit.getInventory(), profile.ammoId())
                        >= profile.toBoomstickProfile().ammoPerVolley())
                .orElse(false);
    }

    @Override
    public boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        if (recruit == null) {
            return false;
        }
        // Keep the Artillery transaction independent from Recruits' optional vanilla-arrow setting.
        return artilleryProfile(weapon)
                .map(profile -> ArtilleryAmmoAccess.consume(
                        recruit.getInventory(),
                        profile.ammoId(),
                        profile.toBoomstickProfile().ammoPerVolley()))
                .orElse(false);
    }

    @Override
    public ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition) {
        Optional<ArtilleryWeaponProfile> profileResult = artilleryProfile(weapon);
        if (profileResult.isEmpty()) {
            return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
        }
        if (recruit == null || !recruit.isAlive()) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }
        if (!(recruit.level() instanceof ServerLevel serverLevel)) {
            return new ShotResult(
                    recruit.level().isClientSide ? ShotOutcome.CLIENT_SIDE_REJECTED : ShotOutcome.INVALID_TARGET,
                    0);
        }
        if (targetPosition == null || !isFinite(targetPosition)) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }

        ArtilleryWeaponProfile profile = profileResult.orElseThrow();
        if (!ArtilleryNativeState.isLoaded(weapon, profile)) {
            return new ShotResult(ShotOutcome.NOT_LOADED, 0);
        }

        ItemStack originalWeapon = weapon.copy();
        AbstractArrow projectile = null;
        try {
            ResourceLocation projectileId = ResourceLocation.tryParse(profile.projectileEntityId());
            EntityType<?> entityType = projectileId == null
                    ? null
                    : ForgeRegistries.ENTITY_TYPES.getValue(projectileId);
            if (entityType == null) {
                return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
            }

            Entity created = entityType.create(serverLevel);
            if (!(created instanceof AbstractArrow arrow)
                    || !supportsProjectileClassName(created.getClass().getName())) {
                if (created != null) {
                    created.remove(Entity.RemovalReason.DISCARDED);
                }
                return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
            }
            projectile = arrow;

            Vec3 origin = new Vec3(recruit.getX(), recruit.getEyeY() - 0.1D, recruit.getZ());
            Vec3 direction = targetPosition.subtract(origin);
            if (direction.lengthSqr() < 1.0E-8D) {
                projectile.remove(Entity.RemovalReason.DISCARDED);
                return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
            }

            projectile.setOwner(recruit);
            projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
            projectile.setBaseDamage(profile.baseDamage());
            projectile.setKnockback(1);
            projectile.setPierceLevel((byte) 0);
            projectile.setSilent(true);
            projectile.setSecondsOnFire(0);
            projectile.setCritArrow(profile.critical());
            projectile.setPos(origin.x, origin.y, origin.z);
            projectile.shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    (float) profile.projectileVelocity(),
                    profile.inaccuracy());

            if (!serverLevel.addFreshEntity(projectile)) {
                projectile.remove(Entity.RemovalReason.DISCARDED);
                return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
            }

            weapon.hurtAndBreak(1, recruit,
                    ignored -> recruit.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
            ArtilleryNativeState.markFired(weapon, profile);
            ArtilleryNativeState.setFiring(weapon, true);
            playShotEffectsSafely(serverLevel, recruit, profile, origin);
            return new ShotResult(ShotOutcome.FIRED, 1);
        } catch (RuntimeException | LinkageError exception) {
            if (projectile != null) {
                projectile.remove(Entity.RemovalReason.DISCARDED);
            }
            restoreStack(weapon, originalWeapon);
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Artillery Arquebus shot failed for recruit {}",
                    recruit.getId(),
                    exception);
            return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
        }
    }

    private static void playShotEffectsSafely(
            ServerLevel level,
            CrossBowmanEntity recruit,
            ArtilleryWeaponProfile profile,
            Vec3 origin
    ) {
        try {
            SoundEvent sound = soundFor(profile.firingSound());
            level.playSound(
                    null,
                    recruit.getX(),
                    recruit.getY(),
                    recruit.getZ(),
                    sound == null ? SoundEvents.CROSSBOW_SHOOT : sound,
                    SoundSource.PLAYERS,
                    5.0F,
                    1.0F);
            if (CompatConfig.SMOKE_PARTICLES.get()) {
                level.sendParticles(
                        ParticleTypes.SMOKE,
                        origin.x,
                        origin.y,
                        origin.z,
                        5,
                        0.08D,
                        0.08D,
                        0.08D,
                        0.02D);
            }
        } catch (RuntimeException exception) {
            RecruitsUseBoomsticks.LOGGER.warn("Artillery shot effects failed", exception);
        }
    }

    private static SoundEvent soundFor(BoomstickSound sound) {
        if (sound == BoomstickSound.ARTILLERY_FIRE) {
            ResourceLocation soundId = ResourceLocation.tryParse(ARQUEBUS_FIRE_SOUND);
            SoundEvent artillerySound = soundId == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(soundId);
            return artillerySound == null ? SoundEvents.CROSSBOW_SHOOT : artillerySound;
        }
        return SoundEvents.CROSSBOW_SHOOT;
    }

    private static void restoreStack(ItemStack target, ItemStack snapshot) {
        target.setCount(snapshot.getCount());
        target.setDamageValue(snapshot.getDamageValue());
        target.setTag(snapshot.hasTag() ? snapshot.getTag().copy() : null);
    }

    private static String registryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static boolean hasNamedSuperclass(Class<?> type, String expectedName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (expectedName.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
