package org.iwoss.recruits_use_boomsticks.mixin;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickCombatPolicy;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecruitRangedCrossbowAttackGoal.class)
public abstract class RecruitRangedCrossbowAttackGoalMixin {
    @Shadow(remap = false) @Final private CrossBowmanEntity crossBowman;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$disableForBoomsticks(
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (BoomstickCombatPolicy.shouldSuppressOriginalGoal(
                CompatConfig.ENABLED.get(),
                hasBoomstickWeapon())) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$stopForBoomsticks(
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (BoomstickCombatPolicy.shouldSuppressOriginalGoal(
                CompatConfig.ENABLED.get(),
                hasBoomstickWeapon())) {
            callbackInfo.setReturnValue(false);
        }
    }

    private boolean hasBoomstickWeapon() {
        ItemStack mainHand = crossBowman.getMainHandItem();
        if (SupportedBoomsticks.isSupportedWeapon(mainHand)) {
            return true;
        }
        ItemStack inventoryWeapon = crossBowman.getMatchingItem(SupportedBoomsticks::isSupportedWeapon);
        return inventoryWeapon != null && !inventoryWeapon.isEmpty();
    }
}
