package org.iwoss.recruits_use_boomsticks.client;

import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.Optional;

/**
 * Chooses the arm pose a recruit uses while carrying a supported ranged weapon.
 *
 * <p>This is display policy only. It reads weapon state through the same adapters the combat goal
 * uses and never writes anything, so a rendering failure can never corrupt a loading transaction.</p>
 */
public final class BoomstickArmPose {
    private BoomstickArmPose() {
    }

    /**
     * Returns the pose to show, or empty to leave the renderer's own choice alone.
     *
     * <p>A loaded firearm is shouldered with both hands. A firearm mid-reload uses the charging
     * pose, while a physical throwing weapon uses the spear-throwing pose.</p>
     */
    public static Optional<HumanoidModel.ArmPose> firearmPose(
            AbstractInventoryEntity entity,
            ItemStack held
    ) {
        if (entity == null || held == null || held.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (!CompatConfig.ENABLED.get()) {
                return Optional.empty();
            }
            RecruitWeaponAdapters adapters = RecruitWeaponAdapters.production();
            Optional<BoomstickWeaponAdapter> adapter = adapters.findEnabled(held);
            if (adapter.isEmpty()) {
                return Optional.empty();
            }
            BoomstickWeaponAdapter selected = adapter.orElseThrow();
            if (selected.isThrowable(held)) {
                // The raised arm is the wind-up itself, so it belongs to the aim window rather than
                // to merely holding the weapon: a recruit walking into range carries it at its side
                // and only cocks it back once it has a shot it can take.
                return Optional.of(selected.isAiming(held)
                        && !BoomstickCarryClientState.isCarrying(entity.getId())
                        ? HumanoidModel.ArmPose.THROW_SPEAR
                        : HumanoidModel.ArmPose.ITEM);
            }
            if (selected.isReloading(held)) {
                return Optional.of(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
            }
            if (BoomstickCarryClientState.isCarrying(entity.getId())) {
                // A recruit that drew its firearm on command carries it upright in the main hand
                // instead of levelling it, so an ordered company does not look mid-volley on the march.
                return Optional.of(HumanoidModel.ArmPose.ITEM);
            }
            return Optional.of(selected.isLoaded(held)
                    ? HumanoidModel.ArmPose.CROSSBOW_HOLD
                    : HumanoidModel.ArmPose.ITEM);
        } catch (RuntimeException | LinkageError exception) {
            // Rendering must never take the game down over a pose decision.
            return Optional.empty();
        }
    }
}
