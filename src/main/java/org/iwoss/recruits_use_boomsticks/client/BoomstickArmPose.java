package org.iwoss.recruits_use_boomsticks.client;

import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.Optional;

/**
 * Chooses the arm pose a recruit uses while carrying a supported firearm.
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
     * <p>A loaded weapon is shouldered with both hands. A weapon mid-reload uses the charging pose,
     * which keeps the recruit's hands on the weapon while it works through powder, ball, and
     * ramrod.</p>
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
