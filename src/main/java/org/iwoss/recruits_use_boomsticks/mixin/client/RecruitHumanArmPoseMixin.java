package org.iwoss.recruits_use_boomsticks.mixin.client;

import com.talhanation.recruits.client.render.RecruitHumanRenderer;
import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.client.BoomstickArmPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Human-model counterpart of {@link RecruitArmPoseMixin}; Recruits ships one renderer per skin. */
@Mixin(RecruitHumanRenderer.class)
public abstract class RecruitHumanArmPoseMixin {
    @Inject(
            method = "getArmPose",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void recruits_use_boomsticks$holdFirearmsTwoHanded(
            AbstractInventoryEntity entity,
            InteractionHand hand,
            CallbackInfoReturnable<HumanoidModel.ArmPose> callback
    ) {
        if (callback.getReturnValue() != HumanoidModel.ArmPose.ITEM || entity == null) {
            return;
        }
        ItemStack held = entity.getItemInHand(hand);
        BoomstickArmPose.firearmPose(entity, held).ifPresent(callback::setReturnValue);
    }
}
