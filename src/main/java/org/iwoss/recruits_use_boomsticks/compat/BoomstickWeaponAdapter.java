package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Boundary between recruit AI and Medieval Boomsticks implementation details. */
public interface BoomstickWeaponAdapter {
    boolean supports(ItemStack weapon);

    Optional<BoomstickWeaponProfile> profile(ItemStack weapon);

    boolean isLoaded(ItemStack weapon);

    void setLoaded(ItemStack weapon, boolean loaded);

    void setReloading(ItemStack weapon, boolean reloading);

    void setFiring(ItemStack weapon, boolean firing);

    int reloadTicks(ItemStack weapon);

    boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired);

    boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired);

    ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition);

    enum ShotOutcome {
        FIRED,
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
}
