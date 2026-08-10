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

    boolean isLoaded(ItemStack weapon);

    void setLoaded(ItemStack weapon, boolean loaded);

    void setReloading(ItemStack weapon, boolean reloading);

    boolean isReloading(ItemStack weapon);

    void setFiring(ItemStack weapon, boolean firing);

    /** Clears animation-only state without changing a committed loaded payload. */
    default void clearTransientState(ItemStack weapon) {
        setReloading(weapon, false);
        setFiring(weapon, false);
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
