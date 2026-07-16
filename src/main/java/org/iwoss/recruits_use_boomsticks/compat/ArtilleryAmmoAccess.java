package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact registry-identity ammo access for Artillery Addon inventory transactions. */
public final class ArtilleryAmmoAccess {
    private ArtilleryAmmoAccess() {
    }

    public static int count(Container inventory, String ammoId) {
        Objects.requireNonNull(inventory, "inventory");
        Item ammo = resolve(ammoId);
        if (ammo == null) {
            return 0;
        }

        List<AmmoSlot> slots = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            slots.add(new AmmoSlot(stack.isEmpty() ? "" : registryId(stack), stack.getCount()));
        }
        return countSlots(slots, registryId(ammo));
    }

    /** Counts the complete volley before mutating any stack. */
    public static boolean consume(Container inventory, String ammoId, int amount) {
        Objects.requireNonNull(inventory, "inventory");
        if (amount < 0) {
            return false;
        }
        if (amount == 0) {
            return true;
        }

        Item ammo = resolve(ammoId);
        if (ammo == null) {
            return false;
        }

        List<AmmoSlot> slots = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            slots.add(new AmmoSlot(stack.isEmpty() ? "" : registryId(stack), stack.getCount()));
        }

        Optional<int[]> consumption = planConsumption(slots, registryId(ammo), amount);
        if (consumption.isEmpty()) {
            return false;
        }

        int[] consumedPerSlot = consumption.orElseThrow();
        for (int slot = 0; slot < consumedPerSlot.length; slot++) {
            int consumed = consumedPerSlot[slot];
            if (consumed == 0) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != ammo || stack.getCount() < consumed) {
                throw new IllegalStateException("Artillery ammo inventory changed during consumption");
            }
            stack.shrink(consumed);
            inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        inventory.setChanged();
        return true;
    }

    static int countSlots(List<AmmoSlot> slots, String ammoId) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(ammoId, "ammoId");
        int count = 0;
        for (AmmoSlot slot : slots) {
            if (ammoId.equals(slot.registryId())) {
                count += slot.count();
            }
        }
        return count;
    }

    /** Plans a complete transaction without mutating the inventory. */
    static Optional<int[]> planConsumption(List<AmmoSlot> slots, String ammoId, int amount) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(ammoId, "ammoId");
        if (amount < 0 || countSlots(slots, ammoId) < amount) {
            return Optional.empty();
        }

        int[] consumedPerSlot = new int[slots.size()];
        int remaining = amount;
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            AmmoSlot candidate = slots.get(slot);
            if (!ammoId.equals(candidate.registryId())) {
                continue;
            }
            int consumed = Math.min(remaining, candidate.count());
            consumedPerSlot[slot] = consumed;
            remaining -= consumed;
        }
        if (remaining != 0) {
            throw new IllegalStateException("Artillery ammo plan did not consume the requested amount");
        }
        return Optional.of(consumedPerSlot);
    }

    private static Item resolve(String ammoId) {
        ResourceLocation id = ResourceLocation.tryParse(ammoId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(id);
    }

    private static String registryId(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static String registryId(Item item) {
        var key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    record AmmoSlot(String registryId, int count) {
        AmmoSlot {
            Objects.requireNonNull(registryId, "registryId");
            if (count < 0) {
                throw new IllegalArgumentException("count must be non-negative");
            }
        }
    }
}
