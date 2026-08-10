package org.iwoss.recruits_use_boomsticks.mixin.client;

import com.talhanation.recruits.client.gui.CommandScreen;
import com.talhanation.recruits.client.gui.commandscreen.CombatCategory;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.world.entity.player.Player;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.client.BoomstickCarryButton;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds the firearm carry order to Recruits' combat command tab.
 *
 * <p>The tab is rebuilt from scratch every time it is opened, so appending one button at the tail of
 * the upstream layout keeps every existing button exactly where the player expects it.</p>
 */
@Mixin(CombatCategory.class)
public abstract class CombatCategoryMixin {
    @Unique
    private static boolean recruits_use_boomsticks$reported;

    @Inject(method = "createButtons", at = @At("TAIL"), remap = false)
    private void recruits_use_boomsticks$addCarryButton(
            CommandScreen screen,
            int x,
            int y,
            List<RecruitsGroup> groups,
            Player player,
            CallbackInfo callback
    ) {
        if (!CompatConfig.ENABLED.get()) {
            return;
        }
        if (!recruits_use_boomsticks$reported) {
            recruits_use_boomsticks$reported = true;
            // Once per session: the cheapest way to tell "the button is missing" from "the button did
            // nothing" when a player reports the order not working.
            RecruitsUseBoomsticks.LOGGER.debug(
                    "Carry order buttons attached to the Recruits combat tab ({} groups)", groups.size());
        }
        BoomstickCarryButton.add(screen, x, y, groups);
    }
}
