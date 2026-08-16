package org.iwoss.recruits_use_boomsticks.client;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

/** Drops reload interpolation state once the recruit it belongs to is gone. */
@Mod.EventBusSubscriber(
        modid = RecruitsUseBoomsticks.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class BoomstickCarryClientEvents {
    private BoomstickCarryClientEvents() {
    }

    /**
     * The renderer only forgets a clock on the frame it sees the reloading marker gone. A recruit
     * that dies, unloads, or simply walks out of tracking range mid-reload is never drawn again, so
     * without this its entry would sit in the map for the rest of the session.
     */
    @SubscribeEvent
    public static void onLeavingLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof CrossBowmanEntity recruit) {
            BoomstickReloadProgress.clear(recruit.getId());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BoomstickReloadProgress.clearAll();
    }
}
