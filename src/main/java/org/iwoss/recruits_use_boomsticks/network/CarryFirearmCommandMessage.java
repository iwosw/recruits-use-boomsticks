package org.iwoss.recruits_use_boomsticks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.command.BoomstickCarryOrder;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The command screen's "take firearms out" order.
 *
 * <p>The owner is taken from the connection, never from the payload, so a client cannot order
 * another player's recruits around. All selected target groups travel in one bounded message.</p>
 */
public record CarryFirearmCommandMessage(List<UUID> groups, boolean draw) {
    /** Stands for Recruits' "everyone" selection: every recruit the sender owns, whatever its group. */
    public static final UUID EVERYONE = new UUID(0L, 0L);
    /** Largest selection one order carries; the sender cuts a longer one rather than being refused. */
    public static final int MAX_GROUPS = 64;
    /**
     * Floor between two accepted orders.
     *
     * <p>A rejected order is dropped without a report, so this only bounds a flooding client and
     * must stay below the interval a player can actually click in: the draw and stow buttons sit
     * next to each other and are pressed in sequence.</p>
     */
    static final int COMMAND_COOLDOWN_TICKS = 2;
    private static final String COOLDOWN_UNTIL_TAG =
            RecruitsUseBoomsticks.MOD_ID + ":carry_command_cooldown_until";

    public CarryFirearmCommandMessage {
        Objects.requireNonNull(groups, "groups");
        if (groups.isEmpty() || groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("carry order must contain 1.." + MAX_GROUPS + " groups");
        }
        groups = List.copyOf(groups);
    }

    public static void encode(CarryFirearmCommandMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.groups().size());
        message.groups().forEach(buffer::writeUUID);
        buffer.writeBoolean(message.draw());
    }

    public static CarryFirearmCommandMessage decode(FriendlyByteBuf buffer) {
        int groupCount = buffer.readVarInt();
        if (groupCount < 1 || groupCount > MAX_GROUPS) {
            throw new IllegalArgumentException("invalid carry order group count " + groupCount);
        }
        List<UUID> groups = new ArrayList<>(groupCount);
        for (int index = 0; index < groupCount; index++) {
            groups.add(buffer.readUUID());
        }
        return new CarryFirearmCommandMessage(groups, buffer.readBoolean());
    }

    public static void handle(CarryFirearmCommandMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null && acquire(sender.getPersistentData(), sender.level().getGameTime())) {
            if (CompatConfig.DEBUG_LOGGING.get()) {
                RecruitsUseBoomsticks.LOGGER.info(
                        "Carry order received from {}: groups={}, draw={}",
                        sender.getGameProfile().getName(),
                        message.groups(),
                        message.draw());
            }
            BoomstickCarryOrder.apply(sender, message.groups(), message.draw());
        }
        ctx.setPacketHandled(true);
    }

    static boolean acquire(CompoundTag playerData, long gameTime) {
        long cooldownUntil = playerData.getLong(COOLDOWN_UNTIL_TAG);
        if (cooldownUntil > gameTime) {
            return false;
        }
        playerData.putLong(COOLDOWN_UNTIL_TAG, gameTime + COMMAND_COOLDOWN_TICKS);
        return true;
    }
}
