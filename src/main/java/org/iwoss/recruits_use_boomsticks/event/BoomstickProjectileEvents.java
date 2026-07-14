package org.iwoss.recruits_use_boomsticks.event;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile;
import com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickProjectilePolicy;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

/** Prevents recruit-owned Boomsticks projectiles from damaging allies or the owner. */
@Mod.EventBusSubscriber(
        modid = RecruitsUseBoomsticks.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BoomstickProjectileEvents {
    private BoomstickProjectileEvents() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        boolean supportedProjectile = projectile instanceof RoundBallProjectile
                || projectile instanceof HeavyBoltProjectile;
        if (!BoomstickProjectilePolicy.shouldApply(
                CompatConfig.ENABLED.get(),
                supportedProjectile,
                projectile.getOwner() instanceof AbstractRecruitEntity)) {
            return;
        }

        if (!(projectile.getOwner() instanceof AbstractRecruitEntity recruit)) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) {
            return;
        }

        Entity hitEntity = entityHit.getEntity();
        if (hitEntity == recruit) {
            event.setImpactResult(ImpactResult.SKIP_ENTITY);
            return;
        }
        if (hitEntity instanceof LivingEntity living
                && (recruit.isAlliedTo(hitEntity) || !recruit.canAttack(living))) {
            event.setImpactResult(ImpactResult.SKIP_ENTITY);
        }
    }
}
