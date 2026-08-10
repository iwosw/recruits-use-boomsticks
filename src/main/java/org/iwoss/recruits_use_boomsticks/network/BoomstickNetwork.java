package org.iwoss.recruits_use_boomsticks.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

/**
 * The compatibility layer's own play channel.
 *
 * <p>Recruits' command screen only speaks its own packets, so the carry order and the pose flag it
 * produces need a channel this mod owns. The channel is optional on both ends: a client without the
 * mod simply never sends the order and never receives the flag.</p>
 */
public final class BoomstickNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RecruitsUseBoomsticks.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private BoomstickNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.messageBuilder(CarryFirearmCommandMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CarryFirearmCommandMessage::encode)
                .decoder(CarryFirearmCommandMessage::decode)
                .consumerMainThread(CarryFirearmCommandMessage::handle)
                .add();

        CHANNEL.messageBuilder(CarryFirearmStateMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CarryFirearmStateMessage::encode)
                .decoder(CarryFirearmStateMessage::decode)
                .consumerMainThread(CarryFirearmStateMessage::handle)
                .add();
    }
}
