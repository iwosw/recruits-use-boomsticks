package org.iwoss.recruits_use_boomsticks.mixin;

import com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$guardFriendlyFire(
            Entity target,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        AbstractArrow projectile = (AbstractArrow) (Object) this;
        if (!(projectile instanceof RoundBallProjectile)
                && !(projectile instanceof HeavyBoltProjectile)) {
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
}
