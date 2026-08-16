package org.iwoss.recruits_use_boomsticks.mixin;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.entities.ai.compat.RecruitRangedMusketAttackGoal;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickCombatPolicy;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecruitRangedMusketAttackGoal.class)
public abstract class RecruitRangedMusketAttackGoalMixin {
    private static final RecruitWeaponAdapters RECRUIT_WEAPON_ADAPTERS = RecruitWeaponAdapters.production();

    @Shadow(remap = false) @Final private CrossBowmanEntity crossBowman;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$disableForBoomsticks(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (shouldYieldToBoomstick()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$stopForBoomsticks(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (shouldYieldToBoomstick()) {
            callbackInfo.setReturnValue(false);
        }
    }

    /**
     * The inventory scan is deliberately reached only when the configuration is on and the held
     * weapon cannot answer: it walks every slot, and each slot now runs a full ammunition and
     * loading-component preflight.
     */
    private boolean shouldYieldToBoomstick() {
        if (!CompatConfig.ENABLED.get()) {
            return false;
        }
        boolean heldWeapon = RECRUIT_WEAPON_ADAPTERS.isUsableEnabledWeapon(
                crossBowman,
                crossBowman.getMainHandItem());
        return BoomstickCombatPolicy.shouldSuppressOriginalGoal(
                true,
                heldWeapon,
                !heldWeapon && hasSupportedInventoryWeapon());
    }

    private boolean hasSupportedInventoryWeapon() {
        ItemStack inventoryWeapon = crossBowman.getMatchingItem(
                stack -> RECRUIT_WEAPON_ADAPTERS.isUsableEnabledWeapon(crossBowman, stack));
        return inventoryWeapon != null && !inventoryWeapon.isEmpty();
    }
}
