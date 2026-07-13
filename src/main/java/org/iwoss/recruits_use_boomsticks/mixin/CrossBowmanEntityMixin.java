package org.iwoss.recruits_use_boomsticks.mixin;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.ai.RecruitBoomstickAttackGoal;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickAmmoAccess;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossBowmanEntity.class)
public abstract class CrossBowmanEntityMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void recruitsUseBoomsticks$addGoal(CallbackInfo callbackInfo) {
        CrossBowmanEntity recruit = (CrossBowmanEntity) (Object) this;
        recruit.goalSelector.addGoal(0, new RecruitBoomstickAttackGoal(recruit, 1.0D));
    }

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$acceptBoomsticks(
            ItemStack stack,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!CompatConfig.ENABLED.get() || stack == null || stack.isEmpty()) {
            return;
        }
        if (SupportedBoomsticks.isSupportedWeapon(stack)) {
            callbackInfo.setReturnValue(true);
            return;
        }
        if (SupportedBoomsticks.isSupportedAmmo(stack)) {
            callbackInfo.setReturnValue(BoomstickAmmoAccess.isAmmoRequired());
        }
    }
}
