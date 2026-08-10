package org.iwoss.recruits_use_boomsticks.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

/** Drops the carry flags of a connection that ended, before their entity ids are handed out again. */
@Mod.EventBusSubscriber(
        modid = RecruitsUseBoomsticks.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class BoomstickCarryClientEvents {
    private BoomstickCarryClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BoomstickCarryClientState.clear();
    }
}
