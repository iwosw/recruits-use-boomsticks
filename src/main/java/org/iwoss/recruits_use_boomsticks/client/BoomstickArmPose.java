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
            // Every other firearm state is gripped with both hands, including an empty weapon and
            // the parade carry an order puts a recruit in.
            //
            // ITEM was used for those two before, on the assumption it reads as an upright carry the
            // way it does on a player. It does not: a recruit's arm hangs at its side while the item
            // renders at the humanoid hand point, so a musket floats beside the body instead of
            // being held. A pose the model cannot carry is worse than losing the distinction between
            // a loaded and an empty weapon, which the reload animation and the aim already show.
            return Optional.of(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        } catch (RuntimeException | LinkageError exception) {
            // Rendering must never take the game down over a pose decision.
            return Optional.empty();
        }
    }
}
