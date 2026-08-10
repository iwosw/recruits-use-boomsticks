package org.iwoss.recruits_use_boomsticks.event;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.command.BoomstickCarryOrder;

/** Hands the carry flag to a player the moment the recruit enters their tracking range. */
@Mod.EventBusSubscriber(
        modid = RecruitsUseBoomsticks.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BoomstickCarrySyncEvents {
    private BoomstickCarrySyncEvents() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof CrossBowmanEntity recruit)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (BoomstickCarryOrder.isCarrying(recruit)) {
            BoomstickCarryOrder.sync(recruit, player);
        }
    }
}
