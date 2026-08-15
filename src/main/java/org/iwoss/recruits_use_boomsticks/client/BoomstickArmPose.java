package org.iwoss.recruits_use_boomsticks.client;

import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponIntegration;
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
     * <p>A Boomsticks gun follows the poses a player would show for it: charging while it reloads,
     * shouldered once loaded, and the renderer's own one-handed carry while it is empty. Artillery
     * firearms use the charging and shouldered poses, while a physical throwing weapon uses the
     * spear-throwing pose.</p>
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
                // Active combat always wins over an earlier ready-carry order.
                return Optional.of(selected.isAiming(held)
                        ? HumanoidModel.ArmPose.THROW_SPEAR
                        : HumanoidModel.ArmPose.ITEM);
            }
            if (selected.integration() == RecruitWeaponIntegration.MEDIEVAL_BOOMSTICKS) {
                if (selected.isReloading(held)) {
                    // Boomsticks guns extend CrossbowItem, so the vanilla charge animation reads
                    // their use ticks and charge duration directly and pulls both arms in on time.
                    return Optional.of(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
                }
                if (selected.isLoaded(held)) {
                    // What a player gets for a charged crossbow. Recruits' renderer only reaches this
                    // for a vanilla crossbow and a hardcoded list of muskets, so an arquebus falls
                    // through it and would sit one-handed while a player's is up across the chest.
                    return Optional.of(HumanoidModel.ArmPose.CROSSBOW_HOLD);
                }
                // An unloaded gun is the renderer's business: its own choice already matches a
                // player's, one-handed and level.
                return Optional.empty();
            }
            if (selected.isReloading(held)) {
                return Optional.of(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
            }
            // Non-reloading Artillery firearms keep the supported two-handed stance.
            return Optional.of(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        } catch (RuntimeException | LinkageError exception) {
            // Rendering must never take the game down over a pose decision.
            return Optional.empty();
        }
    }

}
