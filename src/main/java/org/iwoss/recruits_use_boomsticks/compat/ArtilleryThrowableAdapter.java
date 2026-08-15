package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Server-safe Artillery boundary for physical throwing weapons used by recruit ranged AI. */
public final class ArtilleryThrowableAdapter implements BoomstickWeaponAdapter {
    public static final ArtilleryThrowableAdapter INSTANCE = new ArtilleryThrowableAdapter(
            () -> ModList.get().isLoaded(SupportedArtillery.MOD_ID));

    private static final String AIMING_KEY = "recruits_use_boomsticks:throwable_aiming";

    /** Cached artifact shape probe; see {@link #artifactUsesVanillaGroundPickup(ClassLoader)}. */
    private static volatile Boolean vanillaGroundPickupArtifact;
    /** Weapons whose projectile could not be built; reported once each instead of per shot. */
    private static final Set<String> REPORTED_PROJECTILE_FAILURES = ConcurrentHashMap.newKeySet();

    private final BooleanSupplier availability;

    public ArtilleryThrowableAdapter(BooleanSupplier availability) {
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
        // The held stack is the physical projectile; there is no separate loading-ammo family.
        return false;
    }

    @Override
    public boolean supportsProjectile(Class<?> projectileType) {
        if (!isAvailable() || projectileType == null) {
            return false;
        }
        for (Class<?> current = projectileType; current != null; current = current.getSuperclass()) {
            if (SupportedArtilleryThrowables.isSupportedProjectileClassName(current.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean supportsProjectileClassName(String className) {
        return isAvailable()
                && SupportedArtilleryThrowables.isSupportedProjectileClassName(className);
    }

    @Override
    public Optional<BoomstickWeaponProfile> profile(ItemStack weapon) {
        return isAvailable()
                ? SupportedArtilleryThrowables.profileFor(weapon)
                : Optional.empty();
    }

    /** Native per-weapon launch data behind the generic weapon profile. */
    public Optional<SupportedArtilleryThrowables.ArtilleryThrowable> throwable(ItemStack weapon) {
        return isAvailable()
                ? SupportedArtilleryThrowables.throwableFor(weapon)
                : Optional.empty();
    }

    @Override
    public double estimatedVolleyDamage(ItemStack weapon) {
        return throwable(weapon)
                .map(value -> (double) BoomstickDamagePolicy.configuredDamage(
                        (float) value.baseDamage(),
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

    /**
     * The wind-up marker lives on the weapon stack rather than on the recruit, because the client
     * renders the pose from the held item and equipment stacks are already tracked to it.
     *
     * <p>Vanilla would drive this through {@code startUsingItem}, but a recruit must not enter the
     * native item use path: these items implement `finishUsingItem`, so a completed vanilla use
     * would run the native throw procedure a second time and spend another item.</p>
     */
    @Override
    public void setAiming(ItemStack weapon, boolean aiming) {
        if (weapon == null || weapon.isEmpty()) {
            return;
        }
        if (aiming) {
            weapon.getOrCreateTag().putBoolean(AIMING_KEY, true);
            return;
        }
        if (weapon.hasTag()) {
            weapon.getTag().remove(AIMING_KEY);
            if (weapon.getTag().isEmpty()) {
                weapon.setTag(null);
            }
        }
    }

    @Override
    public boolean isAiming(ItemStack weapon) {
        return weapon != null
                && !weapon.isEmpty()
                && weapon.hasTag()
                && weapon.getTag().getBoolean(AIMING_KEY);
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
                        ArtilleryAddonAdapter.maxCompensatedRange(nativeThrowable.velocity())))
                .orElse(maxRange);
    }

    @Override
    public boolean isThrowable(ItemStack weapon) {
        return supports(weapon);
    }

    @Override
    public int aimTicks(ItemStack weapon) {
        return throwable(weapon)
                .map(SupportedArtilleryThrowables.ArtilleryThrowable::useDurationTicks)
                .orElseGet(() -> BoomstickWeaponAdapter.super.aimTicks(weapon));
    }

    @Override
    public int reloadTicks(ItemStack weapon) {
        return 0;
    }

    @Override
    public int cooldownTicks(ItemStack weapon) {
        return supports(weapon) ? SupportedArtilleryThrowables.COOLDOWN_TICKS : 0;
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
        Optional<SupportedArtilleryThrowables.ArtilleryThrowable> throwableResult = throwable(weapon);
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

        SupportedArtilleryThrowables.ArtilleryThrowable nativeThrowable = throwableResult.orElseThrow();
        AbstractArrow projectile = null;
        try {
            Vec3 origin = new Vec3(recruit.getX(), recruit.getEyeY() - 0.1D, recruit.getZ());
            Vec3 direction = ArtilleryAddonAdapter.aimVector(
                    origin,
                    targetPosition,
                    nativeThrowable.velocity());
            if (direction.lengthSqr() < 1.0E-8D) {
                return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
            }

            projectile = createProjectile(serverLevel, nativeThrowable);
            if (projectile == null) {
                return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
            }
            configureProjectile(projectile, recruit, origin, nativeThrowable);
            projectile.shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    (float) nativeThrowable.velocity(),
                    nativeThrowable.inaccuracy());
            if (!serverLevel.addFreshEntity(projectile)) {
                projectile.remove(Entity.RemovalReason.DISCARDED);
                return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
            }

            // Every native ...PlayerFinishesUsingItemProcedure spends one physical held item per
            // full throw. Commit that cost only after the native entity entered the server level.
            weapon.shrink(1);
            return new ShotResult(ShotOutcome.FIRED, 1);
        } catch (RuntimeException | LinkageError exception) {
            if (projectile != null) {
                projectile.remove(Entity.RemovalReason.DISCARDED);
            }
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Artillery {} throw failed for recruit {}",
                    nativeThrowable.weaponId(),
                    recruit.getId(),
                    exception);
            return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
        }
    }

    private static AbstractArrow createProjectile(
            ServerLevel level,
            SupportedArtilleryThrowables.ArtilleryThrowable nativeThrowable
    ) {
        ResourceLocation projectileId = ResourceLocation.tryParse(nativeThrowable.projectileId());
        EntityType<?> entityType = projectileId == null
                ? null
                : ForgeRegistries.ENTITY_TYPES.getValue(projectileId);
        if (entityType == null) {
            reportProjectileFailure(
                    nativeThrowable,
                    "native projectile entity {} is not registered by the installed Artillery artifact");
            return null;
        }

        Entity created = entityType.create(level);
        if (!(created instanceof AbstractArrow arrow)
                || !hasNamedSuperclass(created.getClass(), nativeThrowable.projectileClassName())) {
            if (created != null) {
                created.remove(Entity.RemovalReason.DISCARDED);
            }
            reportProjectileFailure(
                    nativeThrowable,
                    "native projectile entity {} no longer has the expected class shape");
            return null;
        }
        return arrow;
    }

    /**
     * A refused projectile fails the shot closed, which is correct but invisible. An artifact whose
     * entity registration or class shape moved would otherwise look like a recruit that simply never
     * throws, so each weapon reports its first failure and then stays quiet.
     */
    private static void reportProjectileFailure(
            SupportedArtilleryThrowables.ArtilleryThrowable nativeThrowable,
            String reason
    ) {
        if (!REPORTED_PROJECTILE_FAILURES.add(nativeThrowable.weaponId())) {
            return;
        }
        RecruitsUseBoomsticks.LOGGER.warn(
                "Artillery throwing weapon {} is disabled for recruits: " + reason
                        + " (expected class {}); recruits will not throw it",
                nativeThrowable.weaponId(),
                nativeThrowable.projectileId(),
                nativeThrowable.projectileClassName());
    }

    private static void configureProjectile(
            AbstractArrow projectile,
            CrossBowmanEntity recruit,
            Vec3 origin,
            SupportedArtilleryThrowables.ArtilleryThrowable nativeThrowable
    ) {
        projectile.setOwner(recruit);
        projectile.setBaseDamage(nativeThrowable.baseDamage());
        projectile.setKnockback(SupportedArtilleryThrowables.KNOCKBACK);
        projectile.setPierceLevel((byte) 0);
        projectile.setSilent(true);
        projectile.setSecondsOnFire(0);
        projectile.setCritArrow(false);
        projectile.pickup = nativePickup(projectile.getClass());
        projectile.setPos(origin.x, origin.y, origin.z);
    }

    /**
     * Resolves the pickup mode the native procedure would have passed to
     * {@code initArrowProjectile}. Artillery 1.11 passes {@code DISALLOWED} for every throwable and
     * discards the entity in its own {@code onHitBlock} recovery roll; Artillery 1.14 passes
     * {@code ALLOWED} and leaves the embedded projectile collectible.
     *
     * <p>Most classes carry their own marker for that split, so they are answered directly. The
     * Javelin and the throwable cobblestone declare neither method in 1.14, and the cobblestone
     * class is identical in both artifacts, so they fall back to the artifact-wide probe below.</p>
     */
    private static AbstractArrow.Pickup nativePickup(Class<?> projectileType) {
        if (declaresMethod(projectileType, "isStuckInGround")) {
            return AbstractArrow.Pickup.ALLOWED;
        }
        if (declaresMethod(projectileType, "onHitBlock", BlockHitResult.class)) {
            return AbstractArrow.Pickup.DISALLOWED;
        }
        return artifactUsesVanillaGroundPickup(projectileType.getClassLoader())
                ? AbstractArrow.Pickup.ALLOWED
                : AbstractArrow.Pickup.DISALLOWED;
    }

    /**
     * Probes the throwable classes that do carry the 1.14 marker. Falls back to the pinned 1.11
     * behaviour when none of them can be resolved, because that is the artifact this project proves
     * its runtime coverage against.
     */
    private static boolean artifactUsesVanillaGroundPickup(ClassLoader classLoader) {
        Boolean cached = vanillaGroundPickupArtifact;
        if (cached != null) {
            return cached;
        }

        boolean vanillaPickup = false;
        boolean answered = false;
        for (String className : SupportedArtilleryThrowables.supportedProjectileClassNames()) {
            try {
                Class<?> projectileClass = Class.forName(className, false, classLoader);
                answered = true;
                if (declaresMethod(projectileClass, "isStuckInGround")) {
                    vanillaPickup = true;
                    break;
                }
            } catch (ClassNotFoundException | LinkageError exception) {
                // An artifact that does not register this throwable cannot answer the probe.
            }
        }
        if (!answered) {
            // The fallback is a guess about someone else's binary, so it is stated rather than
            // silently applied: a repackaged artifact would otherwise hand out the wrong pickup
            // mode with nothing in the log to explain it.
            RecruitsUseBoomsticks.LOGGER.warn(
                    "No Artillery throwing-weapon projectile class could be resolved to decide native"
                            + " pickup mode; assuming the pinned 1.11 behaviour (not collectible)");
        }
        vanillaGroundPickupArtifact = vanillaPickup;
        return vanillaPickup;
    }

    private static boolean declaresMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            type.getDeclaredMethod(name, parameterTypes);
            return true;
        } catch (NoSuchMethodException | LinkageError exception) {
            return false;
        }
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
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
