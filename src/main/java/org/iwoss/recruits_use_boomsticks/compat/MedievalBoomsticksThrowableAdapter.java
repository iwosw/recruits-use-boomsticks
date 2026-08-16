package org.iwoss.recruits_use_boomsticks.compat;

import com.TBK.medieval_boomsticks.Config;
import com.TBK.medieval_boomsticks.common.registers.MBSounds;
import com.TBK.medieval_boomsticks.server.entity.ThrowableAxe;
import com.TBK.medieval_boomsticks.server.entity.ThrowableKnife;
import com.TBK.medieval_boomsticks.server.entity.ThrowableLargeRock;
import com.TBK.medieval_boomsticks.server.entity.ThrowableSmallRock;
import com.TBK.medieval_boomsticks.server.entity.ThrowableWardart;
import com.TBK.medieval_boomsticks.server.entity.ThrowableWeapon;
import com.TBK.medieval_boomsticks.server.entity.ThrownJavelin;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

import java.util.Optional;

/**
 * Server-safe Medieval Boomsticks boundary for its physical throwing weapons.
 *
 * <p>Every native throw branch is {@code Player}-only — the instant-throw items check
 * {@code Player} inside {@code use}, and the trident-shaped ones do so inside {@code releaseUsing} —
 * so a recruit cannot reach them by using the item. This adapter reproduces the branch instead: it
 * builds the same native projectile from the same held stack, launches it at the same speed, and
 * spends the same one physical item.</p>
 *
 * <p>Damage is deliberately not written onto the projectile. Every Medieval Boomsticks throwable
 * reads its damage from the mod's own configuration inside {@code onHitEntity}, so a recruit's throw
 * hits for exactly what a player's does, and this project's damage settings then apply at the shared
 * {@code LivingHurtEvent} boundary like they do for its firearms.</p>
 */
public final class MedievalBoomsticksThrowableAdapter implements BoomstickWeaponAdapter {
    public static final MedievalBoomsticksThrowableAdapter INSTANCE =
            new MedievalBoomsticksThrowableAdapter();

    /** Set by Medieval Boomsticks on an axe renamed "Cursed" while a player carried it. */
    private static final String CURSED_KEY = "isCursed";

    private MedievalBoomsticksThrowableAdapter() {
    }

    @Override
    public RecruitWeaponIntegration integration() {
        return RecruitWeaponIntegration.MEDIEVAL_BOOMSTICKS;
    }

    @Override
    public boolean supports(ItemStack weapon) {
        return throwable(weapon).isPresent();
    }

    @Override
    public boolean supportsAmmo(ItemStack ammo) {
        // The held stack is the physical projectile; there is no separate loading-ammo family.
        return false;
    }

    @Override
    public boolean supportsProjectile(Class<?> projectileType) {
        return projectileType != null
                && (ThrowableWeapon.class.isAssignableFrom(projectileType)
                || ThrownJavelin.class.isAssignableFrom(projectileType));
    }

    @Override
    public Optional<BoomstickWeaponProfile> profile(ItemStack weapon) {
        return throwable(weapon)
                .map(nativeThrowable -> nativeThrowable.profile(velocity(nativeThrowable)));
    }

    /** Native per-weapon launch data behind the generic weapon profile. */
    public Optional<SupportedMedievalThrowables.MedievalThrowable> throwable(ItemStack weapon) {
        return SupportedMedievalThrowables.throwableFor(weapon);
    }

    @Override
    public double estimatedVolleyDamage(ItemStack weapon) {
        return throwable(weapon)
                .map(nativeThrowable -> (double) BoomstickDamagePolicy.configuredDamage(
                        (float) damage(nativeThrowable),
                        integration()))
                .orElseGet(() -> BoomstickWeaponAdapter.super.estimatedVolleyDamage(weapon));
    }

    @Override
    public boolean isLoaded(ItemStack weapon) {
        // A throwing weapon has no staged payload: every item in the held stack is one ready throw.
        return supports(weapon);
    }

    @Override
    public void setLoaded(ItemStack weapon, boolean loaded) {
    }

    @Override
    public void setReloading(ItemStack weapon, boolean reloading) {
    }

    @Override
    public boolean isReloading(ItemStack weapon) {
        return false;
    }

    @Override
    public void setFiring(ItemStack weapon, boolean firing) {
    }

    /** @see BoomstickAimMarker */
    @Override
    public void setAiming(ItemStack weapon, boolean aiming) {
        BoomstickAimMarker.set(weapon, aiming);
    }

    @Override
    public boolean isAiming(ItemStack weapon) {
        return BoomstickAimMarker.isSet(weapon);
    }

    @Override
    public boolean isThrowable(ItemStack weapon) {
        return supports(weapon);
    }

    /**
     * The javelin draws its wind-up from the vanilla use state.
     *
     * <p>Medieval Boomsticks registers a {@code medieval_boomsticks:aim} item predicate that is only
     * true while the holder is using the stack, and the model it selects is pitched a hundred and
     * seventy degrees away from the carried one. Without the use state a recruit raises its arm
     * holding the carrying model, so the javelin points backwards through the whole throw.</p>
     *
     * <p>Holding it is display only and cannot cost a second throw, for two independent reasons.
     * The goal ends the wind-up with {@code stopUsingItem}, which clears the use state without ever
     * running the item's own {@code releaseUsing}; and every native Medieval Boomsticks release
     * branch refuses a non-player anyway. Nor can the use run to completion and reach an item's
     * finish path: the weapons this returns true for declare a use duration far longer than any
     * wind-up. The instantly thrown knife and axe declare no use duration at all and are excluded by
     * the test below.</p>
     */
    @Override
    public boolean windUpUsesNativeItemState(ItemStack weapon) {
        return supports(weapon) && weapon.getUseDuration() > aimTicks(weapon);
    }

    /**
     * A throwing weapon only reaches as far as its own drop compensation can pay for. Beyond that
     * the shot is clipped and lands short, so the goal closes the distance rather than throwing.
     */
    @Override
    public double effectiveRange(ItemStack weapon, double maxRange) {
        return throwable(weapon)
                .map(nativeThrowable -> Math.min(
                        maxRange,
                        BoomstickBallistics.maxCompensatedRange(velocity(nativeThrowable))))
                .orElse(maxRange);
    }

    /**
     * The shared aim window already outlasts every native wind-up these items require, so it is kept
     * whole and only raised if a weapon ever needs longer than the recruit's own discipline.
     */
    @Override
    public int aimTicks(ItemStack weapon) {
        return throwable(weapon)
                .map(nativeThrowable -> Math.max(
                        BoomstickWeaponAdapter.super.aimTicks(weapon),
                        nativeThrowable.windUpTicks()))
                .orElseGet(() -> BoomstickWeaponAdapter.super.aimTicks(weapon));
    }

    @Override
    public int reloadTicks(ItemStack weapon) {
        return 0;
    }

    @Override
    public int cooldownTicks(ItemStack weapon) {
        return supports(weapon) ? SupportedMedievalThrowables.COOLDOWN_TICKS : 0;
    }

    @Override
    public boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        return recruit != null && isLoaded(weapon);
    }

    @Override
    public boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        // Firing owns the one atomic spawn-and-shrink transaction.
        return false;
    }

    @Override
    public ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition) {
        Optional<SupportedMedievalThrowables.MedievalThrowable> throwableResult = throwable(weapon);
        if (throwableResult.isEmpty()) {
            return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
        }
        if (recruit == null || !recruit.isAlive()) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }
        if (!(recruit.level() instanceof ServerLevel serverLevel)) {
            return new ShotResult(
                    recruit.level().isClientSide
                            ? ShotOutcome.CLIENT_SIDE_REJECTED
                            : ShotOutcome.INVALID_TARGET,
                    0);
        }
        if (targetPosition == null || !isFinite(targetPosition)) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }

        SupportedMedievalThrowables.MedievalThrowable nativeThrowable = throwableResult.orElseThrow();
        AbstractArrow projectile = null;
        try {
            double velocity = velocity(nativeThrowable);
            // The native branch launches from the vanilla shooter origin its own projectile
            // constructor picks, so the aim vector is measured from exactly that point.
            Vec3 origin = new Vec3(recruit.getX(), recruit.getEyeY() - 0.1D, recruit.getZ());
            Vec3 direction = BoomstickBallistics.aimVector(origin, targetPosition, velocity);
            if (direction.lengthSqr() < 1.0E-8D) {
                return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
            }

            ItemStack thrown = wearOneThrow(recruit, weapon);
            if (thrown.isEmpty()) {
                // The durability point the native branch spends would have broken the weapon, so it
                // breaks in the recruit's hand instead of being thrown as an empty stack.
                return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
            }

            projectile = createProjectile(serverLevel, recruit, thrown, nativeThrowable);
            configureProjectile(projectile, recruit, thrown, origin, nativeThrowable);
            projectile.shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    (float) velocity,
                    nativeThrowable.inaccuracy());
            if (!serverLevel.addFreshEntity(projectile)) {
                projectile.remove(Entity.RemovalReason.DISCARDED);
                return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
            }

            // Every native throw branch spends the whole held item per throw, whether it shrinks a
            // stack or removes a single durable weapon. Commit that cost only after the native
            // entity entered the server level.
            weapon.shrink(1);
            playThrowSound(serverLevel, recruit, nativeThrowable);
            return new ShotResult(ShotOutcome.FIRED, 1);
        } catch (RuntimeException | LinkageError exception) {
            if (projectile != null) {
                projectile.remove(Entity.RemovalReason.DISCARDED);
            }
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Medieval Boomsticks {} throw failed for recruit {}",
                    nativeThrowable.weaponId(),
                    recruit.getId(),
                    exception);
            return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
        }
    }

    /**
     * Builds the single item the projectile will carry, already bearing the durability point the
     * native branch charges for a throw.
     *
     * <p>The trident-shaped weapons are damaged and then removed from the thrower's inventory, so
     * what a player recovers from the ground is the damaged copy. Doing that on the copy rather than
     * on the held stack keeps the last throw of a nearly spent weapon from handing the projectile an
     * emptied stack, and leaves the held stack untouched until the projectile has actually entered
     * the level.</p>
     *
     * <p>The point is spent through the same durability path the native branch uses, so Unbreaking
     * gets its saving throw and a recruit's weapon wears at exactly a player's rate. That roll also
     * decides the break: asking whether the raw damage value is one short of the maximum would
     * condemn an Unbreaking weapon that the roll went on to spare, and the shot is refused whenever
     * this reports a break — so the weapon has to be gone afterwards or the recruit would refuse,
     * back off, and refuse again forever.</p>
     *
     * @return the stack to throw, or an empty stack when the weapon breaks instead
     */
    private static ItemStack wearOneThrow(CrossBowmanEntity recruit, ItemStack weapon) {
        ItemStack thrown = weapon.copy();
        thrown.setCount(1);
        if (!thrown.isDamageableItem()) {
            return thrown;
        }
        if (!thrown.hurt(1, recruit.getRandom(), null)) {
            return thrown;
        }
        // The roll broke it. Spend the held weapon outright rather than rolling a second time:
        // Unbreaking could spare it there, and the refused shot would leave nothing to make progress.
        weapon.setDamageValue(weapon.getMaxDamage());
        weapon.shrink(1);
        recruit.broadcastBreakEvent(InteractionHand.MAIN_HAND);
        return ItemStack.EMPTY;
    }

    private static AbstractArrow createProjectile(
            ServerLevel level,
            CrossBowmanEntity recruit,
            ItemStack thrown,
            SupportedMedievalThrowables.MedievalThrowable nativeThrowable
    ) {
        return switch (nativeThrowable.family()) {
            case KNIFE -> new ThrowableKnife(level, recruit, thrown);
            case AXE -> new ThrowableAxe(level, recruit, thrown);
            case SMALL_ROCK -> new ThrowableSmallRock(level, recruit, thrown);
            case LARGE_ROCK -> new ThrowableLargeRock(level, recruit, thrown);
            case WAR_DART -> new ThrowableWardart(level, recruit, thrown);
            case JAVELIN -> new ThrownJavelin(level, recruit, thrown);
        };
    }

    private static void configureProjectile(
            AbstractArrow projectile,
            CrossBowmanEntity recruit,
            ItemStack thrown,
            Vec3 origin,
            SupportedMedievalThrowables.MedievalThrowable nativeThrowable
    ) {
        projectile.setOwner(recruit);
        BoomstickProjectileAttribution.mark(projectile, recruit);
        // The native branch leaves a non-creative throw collectible and touches nothing else, so
        // damage, knockback, and pierce level stay at the projectile's own defaults.
        projectile.pickup = AbstractArrow.Pickup.ALLOWED;
        projectile.setPos(origin.x, origin.y, origin.z);
        if (nativeThrowable.family() == SupportedMedievalThrowables.Family.AXE
                && projectile instanceof ThrowableAxe axe
                && thrown.hasTag()
                && thrown.getTag().getBoolean(CURSED_KEY)) {
            // Medieval Boomsticks only writes this flag while a player carries the axe, so a recruit
            // never earns it — but an axe cursed in a player's hands keeps its behaviour when thrown.
            axe.setIsCursed(true);
        }
    }

    private static void playThrowSound(
            ServerLevel level,
            CrossBowmanEntity recruit,
            SupportedMedievalThrowables.MedievalThrowable nativeThrowable
    ) {
        try {
            SoundEvent sound = soundFor(nativeThrowable.throwSound());
            if (sound == null) {
                return;
            }
            level.playSound(
                    null,
                    recruit.getX(),
                    recruit.getY(),
                    recruit.getZ(),
                    sound,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        } catch (RuntimeException | LinkageError exception) {
            // A thrown weapon is already in the air; a missing sound must not undo the shot.
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Medieval Boomsticks throw sound failed for recruit {} with weapon {}",
                    recruit.getId(),
                    nativeThrowable.weaponId(),
                    exception);
        }
    }

    private static SoundEvent soundFor(BoomstickSound sound) {
        return switch (sound) {
            case THROW_WEAPON -> MBSounds.THROW_WEAPON.get();
            case TRIDENT_THROW -> SoundEvents.TRIDENT_THROW;
            default -> null;
        };
    }

    /** Live Medieval Boomsticks speed for this family, or its native default. */
    private static double velocity(SupportedMedievalThrowables.MedievalThrowable nativeThrowable) {
        double configured = switch (nativeThrowable.family()) {
            case SMALL_ROCK -> Config.smallRockSpeed;
            case LARGE_ROCK -> Config.largeRockSpeed;
            case JAVELIN -> Config.javelinSpeed;
            // The knife, the axe, and the war dart are launched at speeds compiled into the item.
            case KNIFE, AXE, WAR_DART -> nativeThrowable.defaultVelocity();
        };
        return SupportedMedievalThrowables.MedievalThrowable.usableOr(
                configured,
                nativeThrowable.defaultVelocity());
    }

    /** Live Medieval Boomsticks damage for this family, or its native default. */
    private static double damage(SupportedMedievalThrowables.MedievalThrowable nativeThrowable) {
        double configured = switch (nativeThrowable.family()) {
            case KNIFE -> Config.thrownKnifeDamage;
            case AXE -> Config.thrownAxeDamage;
            case SMALL_ROCK -> Config.smallRockDamage;
            case LARGE_ROCK -> Config.largeRockDamage;
            // ThrowableWardart.onHitEntity reads the javelin damage setting, not a wardart one.
            case WAR_DART, JAVELIN -> Config.javelinDamage;
        };
        return SupportedMedievalThrowables.MedievalThrowable.usableOr(
                configured,
                nativeThrowable.defaultDamage());
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
