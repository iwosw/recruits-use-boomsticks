package org.iwoss.recruits_use_boomsticks.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

import java.util.function.Predicate;

/**
 * The compatibility layer's own play channel.
 *
 * <p>Recruits' command screen only speaks its own packets, so the carry order needs a channel this
 * mod owns. The channel is optional on both ends; the client enables its buttons only when the
 * remote connection registered it.</p>
 */
public final class BoomstickNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final Predicate<String> ACCEPTED_VERSIONS =
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION);

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RecruitsUseBoomsticks.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            ACCEPTED_VERSIONS,
            ACCEPTED_VERSIONS);

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
    }
}
