package org.iwoss.recruits_use_boomsticks.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickProjectilePolicy;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
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
    private static final RecruitWeaponAdapters RECRUIT_WEAPON_ADAPTERS = RecruitWeaponAdapters.production();

    @Shadow
    private boolean inGround;

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void recruitsUseBoomsticks$guardFriendlyFire(
            Entity target,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        AbstractArrow projectile = (AbstractArrow) (Object) this;
        Entity owner = projectile.getOwner();
        if (!(owner instanceof AbstractRecruitEntity recruitOwner)
                || !(target instanceof LivingEntity livingTarget)) {
            return;
        }
        if (!CompatConfig.ENABLED.get()
                || !BoomstickProjectilePolicy.shouldApply(
                true,
                RECRUIT_WEAPON_ADAPTERS.isSupportedEnabledProjectile(projectile),
                true)) {
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
        // The owner test comes first on purpose: it is the cheapest filter and it keeps the adapter
        // lookup — which reads an entity's persistent data — off every unrelated arrow in the world.
        if (projectile.level().isClientSide
                || !(projectile.getOwner() instanceof AbstractRecruitEntity)
                || !CompatConfig.ENABLED.get()
                || !BoomstickProjectilePolicy.shouldApply(
                        true,
                        RECRUIT_WEAPON_ADAPTERS.isSupportedEnabledProjectile(projectile),
                        true)
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
