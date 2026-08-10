package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;

import java.util.function.Consumer;

/**
 * Clears loading-transaction state that outlived the goal which owned it.
 *
 * <p>The reloading and firing markers live on the weapon stack so the client can render the matching
 * pose, which means they survive serialization. A recruit that is copied, saved, or unloaded halfway
 * through a reload comes back carrying a marker no goal is driving: the combat goal refuses to start
 * while the weapon reports itself reloading, so nothing would ever clear it again and the recruit
 * stays frozen in the loading pose. Recovery runs whenever a recruit enters a level and puts every
 * carried weapon back to a state the goals can own.</p>
 */
public final class BoomstickTransientStateRecovery {
    private BoomstickTransientStateRecovery() {
    }

    public static void recover(CrossBowmanEntity recruit) {
        recover(recruit, RecruitWeaponAdapters.production());
    }

    /**
     * Puts a borrowed loading component back before the recruit's inventory is dropped.
     *
     * <p>A chain in progress parks the recruit's real off-hand item outside the inventory and shows
     * the borrowed tool in its place. A recruit that dies mid-chain never reaches the goal code that
     * would undo that, so the parked item would drop nothing and be lost with the entity.</p>
     */
    public static void recoverBeforeDrops(CrossBowmanEntity recruit) {
        recoverBeforeDrops(recruit, RecruitWeaponAdapters.production());
    }

    static void recoverBeforeDrops(CrossBowmanEntity recruit, RecruitWeaponAdapters adapters) {
        if (recruit == null || recruit.level().isClientSide) {
            return;
        }
        try {
            adapters.forEachAdapter(adapter -> adapter.endSteppedReload(recruit, recruit.getMainHandItem()));
        } catch (RuntimeException | LinkageError exception) {
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Failed to return a borrowed loading component for recruit {}",
                    recruit.getId(),
                    exception);
        }
    }

    static void recover(CrossBowmanEntity recruit, RecruitWeaponAdapters adapters) {
        if (recruit == null || recruit.level().isClientSide) {
            return;
        }
        try {
            adapters.forEachAdapter(adapter -> {
                // A borrowed ramrod or powder flask is returned and the loading lore removed, so a
                // copied recruit does not keep a component stuck in its off hand forever.
                adapter.endSteppedReload(recruit, recruit.getMainHandItem());
                forEachCarriedStack(recruit, stack -> adapter.clearTransientState(stack));
            });
            recruit.stopUsingItem();
        } catch (RuntimeException | LinkageError exception) {
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Failed to recover boomstick loading state for recruit {}",
                    recruit.getId(),
                    exception);
        }
    }

    /**
     * Visits held and stored stacks alike.
     *
     * <p>A wedged marker on a spare weapon would resurface the moment the recruit equipped it.</p>
     */
    private static void forEachCarriedStack(CrossBowmanEntity recruit, Consumer<ItemStack> action) {
        action.accept(recruit.getMainHandItem());
        action.accept(recruit.getOffhandItem());
        Container inventory = recruit.getInventory();
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                action.accept(stack);
            }
        }
    }
}
