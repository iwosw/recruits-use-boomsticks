package org.iwoss.recruits_use_boomsticks.client;

import com.talhanation.recruits.client.gui.CommandScreen;
import com.talhanation.recruits.client.gui.group.RecruitsCommandButton;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.iwoss.recruits_use_boomsticks.network.BoomstickNetwork;
import org.iwoss.recruits_use_boomsticks.network.CarryFirearmCommandMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The firearm carry orders in Recruits' combat command tab.
 *
 * <p>Two explicit buttons rather than one toggle, the way Recruits' own shield orders work. A toggle
 * would have to remember on the client what a whole company is holding, and the moment that guess is
 * wrong the player presses "take them out" and the recruits put their weapons away instead.</p>
 */
public final class BoomstickCarryButton {
    /** Free rows of Recruits' own centre column, which is spaced 25px apart. */
    private static final int DRAW_ROW_OFFSET = -10;
    private static final int STOW_ROW_OFFSET = 15;

    private static final Component TEXT_DRAW =
            Component.translatable("gui.recruits_use_boomsticks.command.text.weapons_out");
    private static final Component TEXT_STOW =
            Component.translatable("gui.recruits_use_boomsticks.command.text.weapons_away");
    private static final Component TOOLTIP_DRAW =
            Component.translatable("gui.recruits_use_boomsticks.command.tooltip.weapons_out");
    private static final Component TOOLTIP_STOW =
            Component.translatable("gui.recruits_use_boomsticks.command.tooltip.weapons_away");
    private static final Component TOOLTIP_UNAVAILABLE =
            Component.translatable("gui.recruits_use_boomsticks.command.tooltip.unavailable");

    private BoomstickCarryButton() {
    }

    public static void add(CommandScreen screen, int x, int y, List<RecruitsGroup> groups) {
        addOrder(screen, x, y + DRAW_ROW_OFFSET, groups, true, TEXT_DRAW, TOOLTIP_DRAW);
        addOrder(screen, x, y + STOW_ROW_OFFSET, groups, false, TEXT_STOW, TOOLTIP_STOW);
    }

    private static void addOrder(
            CommandScreen screen,
            int x,
            int y,
            List<RecruitsGroup> groups,
            boolean draw,
            Component text,
            Component tooltip
    ) {
        RecruitsCommandButton button = new RecruitsCommandButton(x, y, text, pressed -> send(groups, draw));
        boolean available = remoteSupportsOrders();
        // A dead button with the ordinary tooltip reads as a bug. Say which server it is instead.
        button.setTooltip(Tooltip.create(available ? tooltip : TOOLTIP_UNAVAILABLE));
        button.active = available;
        screen.addRenderableWidget(button);
        RecruitsUseBoomsticks.LOGGER.debug("Added carry order button draw={} at {},{}", draw, x, y);
    }

    private static void send(List<RecruitsGroup> groups, boolean draw) {
        if (!remoteSupportsOrders()) {
            return;
        }
        List<UUID> selected = new ArrayList<>();
        for (RecruitsGroup group : groups) {
            if (!group.isDisabled()) {
                selected.add(group.getUUID());
            }
        }
        if (selected.isEmpty()) {
            // No group selected is Recruits' "everyone" case: the order still has to reach the
            // player's own recruits instead of quietly doing nothing.
            selected.add(CarryFirearmCommandMessage.EVERYONE);
        }
        int dropped = selected.size() - CarryFirearmCommandMessage.MAX_GROUPS;
        if (dropped > 0) {
            // Recruits sets no upper bound on how many groups a player may create, so a selection
            // can outgrow what one bounded packet carries. Cutting it is the only honest option:
            // the message would otherwise be rejected out of hand, and Recruits' "everyone" is not
            // an equivalent — it also reaches recruits that belong to no group at all.
            selected = selected.subList(0, CarryFirearmCommandMessage.MAX_GROUPS);
            reportTruncatedOrder(dropped);
        }
        BoomstickNetwork.CHANNEL.sendToServer(new CarryFirearmCommandMessage(selected, draw));
        if (CompatConfig.DEBUG_LOGGING.get()) {
            RecruitsUseBoomsticks.LOGGER.info(
                    "Sent one carry order packet for {} groups, draw={}",
                    selected.size(),
                    draw);
        }
    }

    private static void reportTruncatedOrder(int dropped) {
        RecruitsUseBoomsticks.LOGGER.warn(
                "Carry order selection exceeded {} groups; {} were not sent",
                CarryFirearmCommandMessage.MAX_GROUPS,
                dropped);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(
                            "chat.recruits_use_boomsticks.carry.too_many_groups",
                            CarryFirearmCommandMessage.MAX_GROUPS,
                            dropped),
                    false);
        }
    }

    private static boolean remoteSupportsOrders() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null
                && BoomstickNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
