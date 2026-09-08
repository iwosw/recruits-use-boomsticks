package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Inventory access for native Artillery loading components matched by registry identity or item tag.
 *
 * <p>{@link ArtilleryAmmoAccess} stays the exact-identity ammunition boundary. This class adds the
 * tag-matched tool lookups the native loading chains need, plus the native durability cost.</p>
 */
public final class ArtilleryComponentAccess {
    private ArtilleryComponentAccess() {
    }

    public static boolean matches(ItemStack stack, ArtilleryReloadStep.ComponentRequirement component) {
        Objects.requireNonNull(component, "component");
        if (component.isEmptyHand()) {
            // The native branch compares the hand against an empty stack, so nothing is carried.
            return stack == null || stack.isEmpty();
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (component.kind() == ArtilleryReloadStep.ComponentRequirement.Kind.ITEM) {
            return component.id().equals(registryId(stack));
        }
        TagKey<Item> tag = tagKey(component.id());
        return tag != null && stack.is(tag);
    }

    /** Returns the first inventory slot holding a matching component, or -1. */
    public static int findSlot(Container inventory, ArtilleryReloadStep.ComponentRequirement component) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(component, "component");
        if (component.isEmptyHand()) {
            return -1;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (matches(inventory.getItem(slot), component)) {
                return slot;
            }
        }
        return -1;
    }

    public static int count(Container inventory, ArtilleryReloadStep.ComponentRequirement component) {
        return count(inventory, component, ignored -> true);
    }

    public static int count(
            Container inventory,
            ArtilleryReloadStep.ComponentRequirement component,
            IntPredicate usableSlot
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(usableSlot, "usableSlot");
        if (component.isEmptyHand()) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (usableSlot.test(slot) && matches(stack, component)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Whether the recruit can pay one step of the native chain.
     *
     * <p>A step that accepts the native bare-hand branch is always satisfied; every other step needs
     * one of its accepted components in the inventory.</p>
     */
    public static boolean isSatisfied(Container inventory, ArtilleryReloadStep step) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(step, "step");
        for (ArtilleryReloadStep.ComponentRequirement component : step.components()) {
            if (component.isEmptyHand() || count(inventory, component) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the recruit can pay a whole native chain, not just each step in isolation.
     *
     * <p>A step-by-step presence check is not enough when the same component is spent more than
     * once: a Chu Ko Nu magazine would start on a single arrow and abort at its second round with
     * the first one already gone. Consumed components are therefore counted against the total
     * demand of the chain, while a damaged tool is needed only once because it survives its step.</p>
     */
    public static boolean satisfiesAll(Container inventory, List<ArtilleryReloadStep> steps) {
        return satisfiesAll(inventory, steps, ignored -> true);
    }

    public static boolean satisfiesAll(
            Container inventory,
            List<ArtilleryReloadStep> steps,
            IntPredicate usableSlot
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(usableSlot, "usableSlot");
        Map<ArtilleryReloadStep.ComponentRequirement, Integer> demand = new LinkedHashMap<>();
        for (ArtilleryReloadStep step : steps) {
            ArtilleryReloadStep.ComponentRequirement chosen = select(inventory, step, usableSlot);
            if (chosen == null) {
                return false;
            }
            if (chosen.isEmptyHand()) {
                continue;
            }
            int required = step.componentUse() == ArtilleryReloadStep.ComponentUse.CONSUME_ONE
                    ? demand.getOrDefault(chosen, 0) + 1
                    : Math.max(1, demand.getOrDefault(chosen, 0));
            demand.put(chosen, required);
        }
        for (Map.Entry<ArtilleryReloadStep.ComponentRequirement, Integer> entry : demand.entrySet()) {
            if (count(inventory, entry.getKey(), usableSlot) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Selects the component the recruit actually uses for one step.
     *
     * <p>The native branch order is preserved: a procedure that tests the bare hand before the tool
     * keeps doing so, so a recruit rams such a weapon bare-handed and never spends a ramrod the
     * native code would not have spent. A {@code null} result means no alternative is available.</p>
     */
    public static ArtilleryReloadStep.ComponentRequirement select(Container inventory, ArtilleryReloadStep step) {
        return select(inventory, step, ignored -> true);
    }

    public static ArtilleryReloadStep.ComponentRequirement select(
            Container inventory,
            ArtilleryReloadStep step,
            IntPredicate usableSlot
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(usableSlot, "usableSlot");
        for (ArtilleryReloadStep.ComponentRequirement component : step.components()) {
            if (component.isEmptyHand() || count(inventory, component, usableSlot) > 0) {
                return component;
            }
        }
        return null;
    }

    /**
     * Applies the native cost of one step directly to the stack the recruit is holding.
     *
     * <p>A consumed component is shrunk by one. A tool is damaged by one and only removed when that
     * damage breaks it, matching the native {@code hurt} then {@code shrink} sequence.</p>
     */
    public static void payStepCost(
            ItemStack component,
            ArtilleryReloadStep.ComponentUse componentUse,
            RandomSource random
    ) {
        Objects.requireNonNull(componentUse, "componentUse");
        Objects.requireNonNull(random, "random");
        if (component == null || component.isEmpty()) {
            return;
        }
        if (componentUse == ArtilleryReloadStep.ComponentUse.CONSUME_ONE) {
            component.shrink(1);
            return;
        }
        if (componentUse == ArtilleryReloadStep.ComponentUse.NONE
                || componentUse == ArtilleryReloadStep.ComponentUse.KEEP_TOOL
                || (componentUse == ArtilleryReloadStep.ComponentUse.WIND_WHEELLOCK
                && net.minecraft.util.Mth.nextDouble(random, 1, 2) != 2)) {
            return;
        }
        if (component.isDamageableItem() && component.hurt(1, random, null)) {
            component.shrink(1);
            component.setDamageValue(0);
        }
    }

    /**
     * Pays the native cost of one loading step.
     *
     * <p>A consumed component is shrunk by one. A tool is damaged by one and only removed when that
     * damage breaks it, matching the native {@code hurt} then {@code shrink} sequence.</p>
     */
    public static boolean useOne(
            Container inventory,
            ArtilleryReloadStep.ComponentRequirement component,
            ArtilleryReloadStep.ComponentUse componentUse,
            RandomSource random
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(componentUse, "componentUse");
        Objects.requireNonNull(random, "random");

        int slot = findSlot(inventory, component);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = inventory.getItem(slot);
        if (componentUse == ArtilleryReloadStep.ComponentUse.CONSUME_ONE) {
            stack.shrink(1);
        } else if (componentUse != ArtilleryReloadStep.ComponentUse.NONE
                && componentUse != ArtilleryReloadStep.ComponentUse.KEEP_TOOL
                && (componentUse != ArtilleryReloadStep.ComponentUse.WIND_WHEELLOCK
                || net.minecraft.util.Mth.nextDouble(random, 1, 2) == 2) && stack.isDamageableItem()) {
            if (stack.hurt(1, random, null)) {
                stack.shrink(1);
                stack.setDamageValue(0);
            }
        }
        inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        inventory.setChanged();
        return true;
    }

    private static TagKey<Item> tagKey(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : ItemTags.create(location);
    }

    private static String registryId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
