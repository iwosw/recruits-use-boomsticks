package org.iwoss.recruits_use_boomsticks.mixin;

import com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickProjectilePolicy;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
    private static final int RECRUIT_PROJECTILE_MAX_AGE_TICKS = 200;

    @Shadow
    private boolean inGround;

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$guardFriendlyFire(
            Entity target,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        AbstractArrow projectile = (AbstractArrow) (Object) this;
        boolean supportedProjectile = projectile instanceof RoundBallProjectile
                || projectile instanceof HeavyBoltProjectile;
        if (!BoomstickProjectilePolicy.shouldApply(
                CompatConfig.ENABLED.get(),
                supportedProjectile,
                projectile.getOwner() instanceof AbstractRecruitEntity)) {
            return;
        }
        Entity owner = projectile.getOwner();
        if (!(owner instanceof AbstractRecruitEntity recruitOwner)
                || !(target instanceof LivingEntity livingTarget)) {
            return;
        }
        if (owner == target
                || recruitOwner.isAlliedTo(target)
                || !recruitOwner.canAttack(livingTarget)) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$discardExpiredProjectile(CallbackInfo callbackInfo) {
        AbstractArrow projectile = (AbstractArrow) (Object) this;
        boolean supportedProjectile = projectile instanceof RoundBallProjectile
                || projectile instanceof HeavyBoltProjectile;
        if (projectile.level().isClientSide
                || !BoomstickProjectilePolicy.shouldApply(
                        CompatConfig.ENABLED.get(),
                        supportedProjectile,
                        projectile.getOwner() instanceof AbstractRecruitEntity)
                || !BoomstickProjectilePolicy.shouldDiscard(
                        projectile.pickup == AbstractArrow.Pickup.ALLOWED,
                        inGround,
                        projectile.tickCount,
                        RECRUIT_PROJECTILE_MAX_AGE_TICKS)) {
            return;
        }

        projectile.discard();
        callbackInfo.cancel();
    }
}
