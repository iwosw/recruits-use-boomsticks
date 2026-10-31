package org.iwoss.recruits_use_boomsticks.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickProjectileAttribution;
import org.iwoss.recruits_use_boomsticks.compat.SupportedArtillery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Native grenade fuse, with the recruit projectile retained as the explosion source. */
@Mod.EventBusSubscriber(modid = RecruitsUseBoomsticks.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecruitMortarExplosionEvents {
    private static final List<PendingBlast> PENDING = new ArrayList<>();

    private RecruitMortarExplosionEvents() {}

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow grenade)
                || !(grenade.level() instanceof ServerLevel level)
                || !BoomstickProjectileAttribution.isRecruitOwned(grenade)) {
            return;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(grenade.getType()).toString();
        if (!SupportedArtillery.CLAYNADE_PROJECTILE_ID.equals(id)
                && !SupportedArtillery.IRONNADE_PROJECTILE_ID.equals(id)) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && BoomstickProjectileAttribution.isFriendly(grenade, hit.getEntity())) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        PENDING.add(new PendingBlast(level, grenade, event.getRayTraceResult().getLocation(),
                level.getGameTime() + 45, SupportedArtillery.IRONNADE_PROJECTILE_ID.equals(id) ? 4 : 3));
        grenade.discard();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Iterator<PendingBlast> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingBlast blast = iterator.next();
            if (blast.level.getGameTime() < blast.detonateAt) {
                continue;
            }
            iterator.remove();
            blast.level.explode(blast.grenade, blast.position.x, blast.position.y,
                    blast.position.z, blast.radius, Level.ExplosionInteraction.NONE);
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (!(source instanceof AbstractArrow grenade)
                || !BoomstickProjectileAttribution.isRecruitOwned(grenade)) {
            return;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(grenade.getType()).toString();
        if (SupportedArtillery.CLAYNADE_PROJECTILE_ID.equals(id)
                || SupportedArtillery.IRONNADE_PROJECTILE_ID.equals(id)) {
            event.getAffectedEntities().removeIf(entity ->
                    BoomstickProjectileAttribution.isFriendly(grenade, entity));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private record PendingBlast(ServerLevel level, AbstractArrow grenade, Vec3 position,
                                long detonateAt, float radius) {}
}
