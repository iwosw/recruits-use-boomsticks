package org.iwoss.recruits_use_boomsticks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.command.BoomstickCarryOrder;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The command screen's "take firearms out" order.
 *
 * <p>The owner is taken from the connection, never from the payload, so a client cannot order
 * another player's recruits around. Only the target group travels with the message.</p>
 */
public record CarryFirearmCommandMessage(UUID group, boolean draw) {
    /** Stands for Recruits' "everyone" selection: every recruit the sender owns, whatever its group. */
    public static final UUID EVERYONE = new UUID(0L, 0L);

    public static void encode(CarryFirearmCommandMessage message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.group());
        buffer.writeBoolean(message.draw());
    }

    public static CarryFirearmCommandMessage decode(FriendlyByteBuf buffer) {
        return new CarryFirearmCommandMessage(buffer.readUUID(), buffer.readBoolean());
    }

    public static void handle(CarryFirearmCommandMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            if (CompatConfig.DEBUG_LOGGING.get()) {
                RecruitsUseBoomsticks.LOGGER.info(
                        "Carry order received from {}: group={}, draw={}",
                        sender.getGameProfile().getName(),
                        message.group(),
                        message.draw());
            }
            BoomstickCarryOrder.apply(
                    sender,
                    EVERYONE.equals(message.group()) ? null : message.group(),
                    message.draw());
        }
        ctx.setPacketHandled(true);
    }
}
