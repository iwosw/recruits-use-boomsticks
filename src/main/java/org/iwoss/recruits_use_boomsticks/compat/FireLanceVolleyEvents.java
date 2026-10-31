package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** The unmounted Fire Lance's nine flame bolts arrive in its native 2/2/5 cadence. */
@Mod.EventBusSubscriber(modid = RecruitsUseBoomsticks.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FireLanceVolleyEvents {
    private static final List<PendingShot> PENDING = new ArrayList<>();

    private FireLanceVolleyEvents() {}

    static void schedule(ServerLevel level, CrossBowmanEntity shooter, Vec3 origin, Vec3 direction) {
        long now = level.getGameTime();
        // Non-passenger branch in FireLanceRightclickProcedure: two now, two at +2,
        // then two plus three at +4. The Iron Bits branch adds four now.
        add(level, shooter, origin, direction, now + 2, 2, 5, 1);
        add(level, shooter, origin, direction, now + 4, 2, 6, 1);
        add(level, shooter, origin, direction, now + 4, 2, 6, 1);
        add(level, shooter, origin, direction, now + 4, 1, 6, 2);
    }

    private static void add(ServerLevel level, CrossBowmanEntity shooter, Vec3 origin,
            Vec3 direction, long due, int count, float spread, double damage) {
        for (int i = 0; i < count; i++) {
            PENDING.add(new PendingShot(level, shooter, origin, direction, due, spread, damage));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Iterator<PendingShot> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingShot shot = iterator.next();
            if (shot.level.getGameTime() < shot.due) {
                continue;
            }
            iterator.remove();
            ArtilleryAddonAdapter.spawnDelayedFireLanceShot(shot.level, shot.shooter,
                    shot.origin, shot.direction, shot.spread, shot.damage);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private record PendingShot(ServerLevel level, CrossBowmanEntity shooter, Vec3 origin,
                               Vec3 direction, long due, float spread, double damage) {}
}
