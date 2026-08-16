package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Boundary between recruit AI and integration-specific ranged-weapon details. */
public interface BoomstickWeaponAdapter {
    boolean supports(ItemStack weapon);

    /** Identifies the config switch that owns this adapter. */
    default RecruitWeaponIntegration integration() {
        return RecruitWeaponIntegration.MEDIEVAL_BOOMSTICKS;
    }

    /** Returns whether this integration owns the inventory item as loading ammunition. */
    boolean supportsAmmo(ItemStack ammo);

    /** Returns whether this integration explicitly owns the projectile class and its subclasses. */
    boolean supportsProjectile(Class<?> projectileType);

    /** Returns whether this integration owns this concrete projectile instance. */
    default boolean supportsProjectile(AbstractArrow projectile) {
        return projectile != null && supportsProjectile(projectile.getClass());
    }

    Optional<BoomstickWeaponProfile> profile(ItemStack weapon);

    /**
     * Expected damage of one complete trigger pull after this integration's damage settings.
     *
     * <p>The combat goal uses this only to keep a formation from committing far more projectiles
     * than a shared target can survive. Adapters with native damage data override it; the fallback
     * remains conservative and lets one point of target health justify one projectile.</p>
     */
    default double estimatedVolleyDamage(ItemStack weapon) {
        return profile(weapon)
                .map(value -> (double) Math.max(1, value.projectileCount()))
                .orElse(1.0D);
    }

    boolean isLoaded(ItemStack weapon);

    void setLoaded(ItemStack weapon, boolean loaded);

    void setReloading(ItemStack weapon, boolean reloading);

    boolean isReloading(ItemStack weapon);

    void setFiring(ItemStack weapon, boolean firing);

    /** Whether client rendering should use a throwing pose instead of a shouldered weapon pose. */
    default boolean isThrowable(ItemStack weapon) {
        return false;
    }

    /** Number of uninterrupted target-facing ticks required before this weapon can fire. */
    default int aimTicks(ItemStack weapon) {
        return 12;
    }

    /**
     * Marks the wind-up before a throw so the client can raise the weapon.
     *
     * <p>Weapons whose pose does not change while aiming ignore this.</p>
     */
    default void setAiming(ItemStack weapon, boolean aiming) {
    }

    /** Whether this weapon is currently held in its wind-up. */
    default boolean isAiming(ItemStack weapon) {
        return false;
    }

    /**
     * Whether the recruit should be held in the vanilla item-use state for this wind-up.
     *
     * <p>Some throwing weapons swap their own item model while their holder is using them — Medieval
     * Boomsticks' javelin registers exactly that predicate — so a raised arm alone shows the carried
     * model in a throwing pose, which points the weapon the wrong way. Entering the use state makes
     * the recruit show what a player shows.</p>
     *
     * <p>It stays off by default because the use state is not free: a vanilla use that runs to
     * completion calls the item's own finish path, and for a throwing weapon that means a second
     * native throw at no cost. Only adapters that have confirmed their items cannot complete a use
     * inside the wind-up, and refuse a non-player on release, may turn it on.</p>
     */
    default boolean windUpUsesNativeItemState(ItemStack weapon) {
        return false;
    }

    /**
     * Farthest distance at which a shot from this weapon can still reach its target.
     *
     * <p>Weapons whose projectiles are fast enough to cross the whole combat range keep the shared
     * maximum. A slow projectile cannot, and the goal walks the recruit closer instead.</p>
     */
    default double effectiveRange(ItemStack weapon, double maxRange) {
        return maxRange;
    }

    /**
     * Shows the tool the native shot is taken with, if this weapon has one.
     *
     * <p>The hand-gonne family is lit with a match held in the free hand. This is display only: the
     * tool is borrowed from the recruit's own inventory for the shot and given straight back.</p>
     */
    default void showFiringTool(CrossBowmanEntity recruit, ItemStack weapon) {
    }

    /** Returns a borrowed firing tool to the inventory. */
    default void clearFiringTool(CrossBowmanEntity recruit) {
    }

    /** Clears animation-only state without changing a committed loaded payload. */
    default void clearTransientState(ItemStack weapon) {
        setReloading(weapon, false);
        setFiring(weapon, false);
        setAiming(weapon, false);
    }

    /**
     * Number of native loading steps this weapon needs, or 0 when it uses the single-step reload.
     *
     * <p>Adapters without a captured multi-step chain keep returning 0, so the shared goal drives
     * them exactly as before.</p>
     */
    default int reloadStepCount(ItemStack weapon) {
        return 0;
    }

    /** Number of native loading steps already present on a partially loaded weapon. */
    default int completedReloadSteps(ItemStack weapon) {
        return 0;
    }

    /** Returns whether the recruit currently carries every component the native chain requires. */
    default boolean hasReloadComponents(CrossBowmanEntity recruit, ItemStack weapon) {
        return true;
    }

    /**
     * Commits one native loading step, paying its component cost and showing the tool in the off hand.
     *
     * @return whether the step was committed; a missing component aborts the transaction
     */
    default boolean applyReloadStep(CrossBowmanEntity recruit, ItemStack weapon, int stepIndex) {
        return false;
    }

    /** Restores the off hand and clears transient loading display after a chain ends or aborts. */
    default void endSteppedReload(CrossBowmanEntity recruit, ItemStack weapon) {
    }

    int reloadTicks(ItemStack weapon);

    int cooldownTicks(ItemStack weapon);

    boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired);

    boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired);

    ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition);

    enum ShotOutcome {
        FIRED,
        MISFIRED,
        NO_AMMO,
        INVALID_WEAPON,
        INVALID_TARGET,
        CLIENT_SIDE_REJECTED,
        NOT_LOADED,
        SPAWN_FAILED
    }

    record ShotResult(ShotOutcome outcome, int projectilesSpawned) {
        public ShotResult {
            if (outcome == null) {
                throw new NullPointerException("outcome");
            }
            if (projectilesSpawned < 0) {
                throw new IllegalArgumentException("projectilesSpawned must be non-negative");
            }
        }

        public boolean fired() {
            return outcome == ShotOutcome.FIRED;
        }
    }

    /** Result shape for adapters that need to expose a committed reload transaction. */
    record ReloadResult(boolean reloaded, boolean committed, boolean needsAmmo, int ammoConsumed) {
        public ReloadResult {
            if (ammoConsumed < 0) {
                throw new IllegalArgumentException("ammoConsumed must be non-negative");
            }
            if (needsAmmo && reloaded) {
                throw new IllegalArgumentException("a completed reload cannot still need ammo");
            }
        }

        public static ReloadResult reloaded(int ammoConsumed) {
            return new ReloadResult(true, true, false, ammoConsumed);
        }

        public static ReloadResult awaitingAmmo() {
            return new ReloadResult(false, false, true, 0);
        }

        public static ReloadResult rejected() {
            return new ReloadResult(false, false, false, 0);
        }
    }
}
