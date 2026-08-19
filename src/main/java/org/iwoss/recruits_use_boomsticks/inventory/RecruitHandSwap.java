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

    /**
     * Moves a matching main-hand stack into the off hand and leaves the main hand empty.
     *
     * <p>This is the shoulder-slung look a player gets from its own off hand, and it is what an
     * order to put the weapon away means for a recruit that owns nothing to take instead. Whatever
     * the off hand held moves to storage, and when storage is full the recruit is left exactly as it
     * stood rather than having anything dropped.</p>
     */
    public static boolean stowMainHandInOffHand(
            AbstractInventoryEntity recruit,
            Predicate<ItemStack> wanted
    ) {
        SimpleContainer inventory = recruit == null ? null : recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        ItemStack mainHand = recruit.getMainHandItem().copy();
        if (mainHand.isEmpty() || !wanted.test(mainHand)) {
            return false;
        }
        ItemStack offHand = recruit.getOffhandItem().copy();
        if (!offHand.isEmpty()) {
            int storage = firstFreeStorageSlot(recruit, inventory);
            if (storage < 0) {
                return false;
            }
            recruit.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            inventory.setItem(storage, offHand);
        }
        recruit.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        recruit.setItemInHand(InteractionHand.OFF_HAND, mainHand);
        return true;
    }

    /**
     * Stows the main hand in the off hand while moving a replacement into the main hand.
     *
     * <p>The displaced off-hand stack occupies the replacement's old storage slot, so this rotation
     * remains lossless when every storage slot is full. A replacement already in the off hand is a
     * direct hand-to-hand swap.</p>
     */
    public static boolean rotateMainHandIntoOffHand(
            AbstractInventoryEntity recruit,
            Predicate<ItemStack> replacement
    ) {
        SimpleContainer inventory = recruit == null ? null : recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        int mainSlot = recruit.getInventorySlotIndex(EquipmentSlot.MAINHAND);
        ItemStack mainHand = recruit.getMainHandItem().copy();
        if (mainHand.isEmpty()) {
            return false;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == mainSlot || isArmourSlot(recruit, slot)) {
                continue;
            }
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !replacement.test(candidate)) {
                continue;
            }
            ItemStack replacementStack = candidate.copy();
            EquipmentSlot sourceEquipment = recruit.getEquipmentSlotIndex(slot);
            if (sourceEquipment == EquipmentSlot.OFFHAND) {
                recruit.setItemInHand(InteractionHand.MAIN_HAND, replacementStack);
                recruit.setItemInHand(InteractionHand.OFF_HAND, mainHand);
                return true;
            }
            if (sourceEquipment != null) {
                continue;
            }
            inventory.setItem(slot, recruit.getOffhandItem().copy());
            recruit.setItemInHand(InteractionHand.MAIN_HAND, replacementStack);
            recruit.setItemInHand(InteractionHand.OFF_HAND, mainHand);
            return true;
        }
        return false;
    }

    /**
     * Draws the wanted off-hand stack while restoring a stored replacement into the off hand.
     *
     * <p>The old main-hand stack occupies the replacement's storage slot, so the three-way
     * rotation remains lossless even when ordinary storage is full.</p>
     */
    public static boolean rotateOffHandIntoMainHand(
            AbstractInventoryEntity recruit,
            Predicate<ItemStack> wanted,
            Predicate<ItemStack> offhandReplacement
    ) {
        SimpleContainer inventory = recruit == null ? null : recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        ItemStack offHand = recruit.getOffhandItem().copy();
        if (offHand.isEmpty() || !wanted.test(offHand)) {
            return false;
        }
        ItemStack mainHand = recruit.getMainHandItem().copy();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!RecruitInventorySafety.isStorageSlot(recruit, slot)) {
                continue;
            }
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !offhandReplacement.test(candidate)) {
                continue;
            }
            inventory.setItem(slot, mainHand);
            recruit.setItemInHand(InteractionHand.MAIN_HAND, offHand);
            recruit.setItemInHand(InteractionHand.OFF_HAND, candidate.copy());
            return true;
        }
        return false;
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
