package org.iwoss.recruits_use_boomsticks.mixin.client;

import com.talhanation.recruits.client.render.RecruitVillagerRenderer;
import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.client.BoomstickArmPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a recruit the two-handed firearm pose instead of letting a gun hang from one hand.
 *
 * <p>Recruits' own renderer only reaches {@code CROSSBOW_HOLD} for a vanilla crossbow or for a
 * hardcoded list of Musket Mod description IDs, so every supported Boomsticks and Artillery weapon
 * falls through to the limp {@code ITEM} pose. This is display only and never changes weapon state.</p>
 */
@Mixin(RecruitVillagerRenderer.class)
public abstract class RecruitArmPoseMixin {
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
