package org.iwoss.recruits_use_boomsticks.event;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.ai.BoomstickFireCoordinator;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickTransientStateRecovery;
import org.iwoss.recruits_use_boomsticks.inventory.RecruitInventorySafety;

/** Puts recruits that enter a level mid-reload back into a state the combat goals can own. */
@Mod.EventBusSubscriber(
        modid = RecruitsUseBoomsticks.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BoomstickRecruitStateEvents {
    private BoomstickRecruitStateEvents() {
    }

    @SubscribeEvent
    public static void onRecruitJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof CrossBowmanEntity recruit) {
            // Covers a copied recruit, a spawn egg carrying entity data, and a chunk or save that
            // was written while a loading transaction was still open.
            BoomstickTransientStateRecovery.recover(recruit);
            // Older builds returned loading tools to the first empty container index. Recruits maps
            // indices 0-3 to armour, so repair those saved entities as soon as their chunk loads.
            RecruitInventorySafety.repairInvalidArmour(recruit);
        }
    }

    @SubscribeEvent
    public static void onRecruitDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        BoomstickFireCoordinator.clearTargetShared(event.getEntity().getUUID());
        if (event.getEntity() instanceof CrossBowmanEntity recruit) {
            // This fires at the top of the death sequence, before the recruit's inventory is
            // dropped, so an off-hand item parked by an open loading chain still drops with it.
            BoomstickTransientStateRecovery.recoverBeforeDrops(recruit);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BoomstickFireCoordinator.clearShared();
    }
}
