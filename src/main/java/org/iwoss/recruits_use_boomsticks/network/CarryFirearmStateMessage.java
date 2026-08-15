package org.iwoss.recruits_use_boomsticks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.iwoss.recruits_use_boomsticks.client.BoomstickCarryClientState;

import java.util.function.Supplier;

/**
 * Tells the client which recruits are carrying their firearm on order.
 *
 * <p>The flag is display state: it distinguishes an ordered ready carry from an active aim. It is
 * sent when the order changes and again whenever a player starts
 * tracking the recruit, so a player who walks into range sees the same pose as everyone else.</p>
 */
public record CarryFirearmStateMessage(int entityId, boolean carrying) {
    public static void encode(CarryFirearmStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId());
        buffer.writeBoolean(message.carrying());
    }

    public static CarryFirearmStateMessage decode(FriendlyByteBuf buffer) {
        return new CarryFirearmStateMessage(buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(CarryFirearmStateMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                BoomstickCarryClientState.set(message.entityId(), message.carrying()));
        ctx.setPacketHandled(true);
    }
}
