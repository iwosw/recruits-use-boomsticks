package org.iwoss.recruits_use_boomsticks.inventory;

import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Moves stacks between a recruit's hands and its storage.
 *
 * <p>A recruit's equipment slots are backed by its own inventory slots, so a hand is both a
 * container slot and an equipment slot. Everything here therefore goes through the entity's hand
 * setters wherever a hand is involved and through the container everywhere else, and nothing is ever
 * dropped: a swap that cannot be completed leaves the recruit exactly as it stood.</p>
 *
 * <p>Recruits' own {@code switchMainHandItem} is deliberately not used. It starts its scan past both
 * hand slots, so it cannot recover a weapon deliberately stowed in the shield hand.</p>
 */
public final class RecruitHandSwap {
    private RecruitHandSwap() {
    }

    /** Puts the first matching stack into the main hand, storing whatever was held. */
    public static boolean intoMainHand(AbstractInventoryEntity recruit, Predicate<ItemStack> wanted) {
        return into(recruit, InteractionHand.MAIN_HAND, wanted);
    }

    /** Puts the first matching stack into the off hand, storing whatever was held there. */
    public static boolean intoOffHand(AbstractInventoryEntity recruit, Predicate<ItemStack> wanted) {
        return into(recruit, InteractionHand.OFF_HAND, wanted);
    }

    /** Moves a matching stack out of the off hand into storage. */
    public static boolean outOfOffHand(AbstractInventoryEntity recruit, Predicate<ItemStack> wanted) {
        SimpleContainer inventory = recruit == null ? null : recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        ItemStack held = recruit.getOffhandItem();
        if (held.isEmpty() || !wanted.test(held)) {
            return false;
        }
        int storage = firstFreeStorageSlot(recruit, inventory);
        if (storage < 0) {
            return false;
        }
        ItemStack taken = held.copy();
        recruit.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        inventory.setItem(storage, taken);
        return true;
    }

    private static boolean into(
            AbstractInventoryEntity recruit,
            InteractionHand destination,
            Predicate<ItemStack> wanted
    ) {
        SimpleContainer inventory = recruit == null ? null : recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        int destinationSlot = recruit.getInventorySlotIndex(equipmentSlot(destination));
        ItemStack held = recruit.getItemInHand(destination).copy();
        if (!held.isEmpty() && wanted.test(held)) {
            return true;
        }

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == destinationSlot || isArmourSlot(recruit, slot)) {
                continue;
            }
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !wanted.test(candidate)) {
                continue;
            }
            ItemStack taken = candidate.copy();
            EquipmentSlot sourceEquipment = recruit.getEquipmentSlotIndex(slot);
            if (sourceEquipment != null) {
                // Hand to hand. The displaced stack may not simply take the source hand's place: the
                // recruit would then visibly hold two weapons and the next order would find the wrong
                // one first, so it goes to storage and the source hand is emptied.
                int storage = firstFreeStorageSlot(recruit, inventory);
                if (!held.isEmpty() && storage < 0) {
                    // Keep looking: another matching stack in storage can still swap places safely.
                    continue;
                }
                recruit.setItemInHand(handFor(sourceEquipment), ItemStack.EMPTY);
                if (!held.isEmpty()) {
                    inventory.setItem(storage, held);
                }
            } else {
                inventory.setItem(slot, held);
            }
            recruit.setItemInHand(destination, taken);
            return true;
        }
        return false;
    }

    /** First empty slot that backs no equipment slot, or {@code -1} when storage is full. */
    public static int firstFreeStorageSlot(AbstractInventoryEntity recruit, SimpleContainer inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (RecruitInventorySafety.isStorageSlot(recruit, slot)
                    && inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean isArmourSlot(AbstractInventoryEntity recruit, int slot) {
        EquipmentSlot equipment = recruit.getEquipmentSlotIndex(slot);
        return equipment != null && equipment.getType() == EquipmentSlot.Type.ARMOR;
    }

    private static EquipmentSlot equipmentSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    private static InteractionHand handFor(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
