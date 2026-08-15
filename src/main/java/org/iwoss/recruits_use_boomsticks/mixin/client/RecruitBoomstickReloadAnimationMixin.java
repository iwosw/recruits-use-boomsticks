package org.iwoss.recruits_use_boomsticks.mixin.client;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.client.BoomstickReloadProgress;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponIntegration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stretches the crossbow charging motion across a recruit's whole reload.
 *
 * <p>The vanilla animation is driven by how long the held item stays in use, which for a crossbow
 * is its charge duration. A recruit holds its gun's reloading marker up for considerably longer than
 * that and for no fixed count of ticks, so left alone the pull finishes early and the arms sit frozen
 * for the rest of the window. The same motion is therefore replayed here, at the weapon's own cocking
 * speed and repeating for exactly as long as the marker stands: identical angles, no frozen arms.</p>
 */
@Mixin(HumanoidModel.class)
public abstract class RecruitBoomstickReloadAnimationMixin {
    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void recruits_use_boomsticks$stretchChargeOverReload(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callback
    ) {
        if (!(entity instanceof CrossBowmanEntity recruit)) {
            return;
        }
        try {
            ItemStack weapon = recruit.getMainHandItem();
            BoomstickWeaponAdapter adapter = RecruitWeaponAdapters.production().findEnabled(weapon).orElse(null);
            if (adapter == null || adapter.integration() != RecruitWeaponIntegration.MEDIEVAL_BOOMSTICKS) {
                return;
            }
            if (!adapter.isReloading(weapon)) {
                BoomstickReloadProgress.clear(recruit.getId());
                return;
            }

            // One pull takes as long as the weapon itself takes to cock, so the motion keeps the
            // speed the item was built for however long the goal holds the marker up.
            float progress = BoomstickReloadProgress.progress(
                    recruit.getId(),
                    ageInTicks,
                    adapter.reloadTicks(weapon));

            // The angles below are the ones AnimationUtils.animateCrossbowCharge writes, with the
            // charge fraction taken from the clock above instead of from the entity's use ticks: a
            // recruit never puts the item into use, so the vanilla fraction would stay pinned at zero.
            // zRot is deliberately left alone, exactly as vanilla leaves it, so the arm bob applied
            // later in setupAnim still reads through.
            boolean rightHanded = recruit.getMainArm() == HumanoidArm.RIGHT;
            ModelPart holding = rightHanded ? rightArm : leftArm;
            ModelPart pulling = rightHanded ? leftArm : rightArm;
            holding.yRot = rightHanded ? -0.8F : 0.8F;
            holding.xRot = -0.97079635F;
            pulling.yRot = Mth.lerp(progress, 0.4F, 0.85F);
            pulling.xRot = Mth.lerp(progress, -0.97079635F, -Mth.HALF_PI);
        } catch (RuntimeException | LinkageError exception) {
            // Rendering must never take the game down over an animation.
        }
    }
}
