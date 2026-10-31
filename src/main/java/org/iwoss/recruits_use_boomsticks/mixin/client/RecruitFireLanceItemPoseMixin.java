package org.iwoss.recruits_use_boomsticks.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.compat.SupportedArtillery;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Turns Artillery's upright third-person Fire Lance into a lance carried along a recruit's arm. */
@Mixin(ItemInHandLayer.class)
public abstract class RecruitFireLanceItemPoseMixin {
    @Inject(
            method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    )
    private void recruits_use_boomsticks$layFireLanceAlongArm(
            LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo callback
    ) {
        if (!(entity instanceof AbstractInventoryEntity)
                || stack.isEmpty()
                || stack != entity.getMainHandItem()
                || !CompatConfig.ENABLED.get()
                || !SupportedArtillery.FIRE_LANCE_ID.equals(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
            return;
        }
        // The addon sprite's shaft runs diagonally. Its third-person transform plus the
        // recruit's crossbow pose makes that diagonal stand upright. A quarter turn at the
        // hand lays the shaft forward along the arm for either main-hand side.
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
    }
}
