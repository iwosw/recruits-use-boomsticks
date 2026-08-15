package org.iwoss.recruits_use_boomsticks.inventory;

import com.talhanation.recruits.entities.AbstractInventoryEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;

/** Keeps ordinary inventory stacks out of the slots that back a recruit's equipment. */
public final class RecruitInventorySafety {
    private static final EquipmentSlot[] ARMOUR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private RecruitInventorySafety() {
    }

    /** Whether a container index is ordinary storage rather than a hand or armour slot. */
    public static boolean isStorageSlot(AbstractInventoryEntity recruit, int slot) {
        return recruit != null && recruit.getEquipmentSlotIndex(slot) == null;
    }

    /**
     * Returns a stack to ordinary storage, dropping only the remainder that genuinely does not fit.
     *
     * <p>Recruits stores armour and both hands in the first six indices of the same container. A
     * normal {@code addItem} or first-empty-slot loop therefore treats an empty helmet slot as the
     * front of the backpack. All compatibility transactions go through this method instead.</p>
     */
    public static void putInStorageOrDrop(AbstractInventoryEntity recruit, ItemStack stack) {
        if (recruit == null || stack == null || stack.isEmpty()) {
            return;
        }
        SimpleContainer inventory = recruit.getInventory();
        if (inventory == null) {
            recruit.spawnAtLocation(stack);
            return;
        }

        for (int slot = 0; slot < inventory.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!isStorageSlot(recruit, slot)) {
                continue;
            }
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty() || !ItemStack.isSameItemSameTags(candidate, stack)) {
                continue;
            }
            int room = Math.min(candidate.getMaxStackSize(), inventory.getMaxStackSize())
                    - candidate.getCount();
            if (room > 0) {
                candidate.grow(stack.split(Math.min(room, stack.getCount())).getCount());
                inventory.setItem(slot, candidate);
            }
        }
        for (int slot = 0; slot < inventory.getContainerSize() && !stack.isEmpty(); slot++) {
            if (isStorageSlot(recruit, slot) && inventory.getItem(slot).isEmpty()) {
                int count = Math.min(
                        stack.getCount(),
                        Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()));
                inventory.setItem(slot, stack.split(count));
            }
        }
        if (!stack.isEmpty()) {
            recruit.spawnAtLocation(stack);
        }
        inventory.setChanged();
    }

    /** Repairs stacks written into armour indices by an older compatibility transaction. */
    public static boolean repairInvalidArmour(AbstractInventoryEntity recruit) {
        SimpleContainer inventory = recruit == null ? null : recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        boolean repaired = false;
        for (EquipmentSlot equipmentSlot : ARMOUR_SLOTS) {
            int inventorySlot = recruit.getInventorySlotIndex(equipmentSlot);
            ItemStack stray = inventory.getItem(inventorySlot);
            if (stray.isEmpty() || canOccupy(stray, equipmentSlot, recruit)) {
                continue;
            }

            ItemStack equipped = recruit.getItemBySlot(equipmentSlot);
            ItemStack returnedStray = stray.copy();
            inventory.setItem(inventorySlot, ItemStack.EMPTY);
            if (!equipped.isEmpty() && canOccupy(equipped, equipmentSlot, recruit)) {
                recruit.setItemSlot(equipmentSlot, equipped.copy());
            } else {
                recruit.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                if (!equipped.isEmpty() && !ItemStack.matches(equipped, stray)) {
                    putInStorageOrDrop(recruit, equipped.copy());
                }
            }
            putInStorageOrDrop(recruit, returnedStray);
            repaired = true;
        }
        if (repaired) {
            inventory.setChanged();
        }
        return repaired;
    }

    private static boolean canOccupy(
            ItemStack stack,
            EquipmentSlot slot,
            AbstractInventoryEntity recruit
    ) {
        // Mirrors Recruits' own inventory-menu rule, including its banner-as-headwear exception.
        return stack.canEquip(slot, recruit)
                || (slot == EquipmentSlot.HEAD && stack.getItem() instanceof BannerItem);
    }
}
