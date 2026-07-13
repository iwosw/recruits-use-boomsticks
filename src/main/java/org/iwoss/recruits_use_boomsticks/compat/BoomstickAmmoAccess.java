package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Finds and atomically consumes the ammo family required by a weapon profile. */
public final class BoomstickAmmoAccess {
    private BoomstickAmmoAccess() {
    }

    /** Reads Recruits' setting instead of introducing a second ammo policy. */
    public static boolean isAmmoRequired() {
        return RecruitsServerConfig.RangedRecruitsNeedArrowsToShoot.get();
    }

    public static int requiredAmmoUnits(BoomstickWeaponProfile profile, boolean ammoRequired) {
        Objects.requireNonNull(profile, "profile");
        return ammoRequired ? profile.ammoPerVolley() : 0;
    }

    public static boolean hasAmmo(
            CrossBowmanEntity recruit,
            BoomstickWeaponProfile profile,
            boolean ammoRequired
    ) {
        Objects.requireNonNull(recruit, "recruit");
        Objects.requireNonNull(profile, "profile");
        if (!ammoRequired) {
            return true;
        }
        return countAmmo(recruit.getInventory(), profile.ammoType())
                >= requiredAmmoUnits(profile, true);
    }

    /**
     * Consumes one complete volley only after the whole inventory has been counted. This keeps
     * an insufficient three-ball volley from partially mutating multiple stacks.
     */
    public static boolean consumeAmmo(
            CrossBowmanEntity recruit,
            BoomstickWeaponProfile profile,
            boolean ammoRequired
    ) {
        Objects.requireNonNull(recruit, "recruit");
        Objects.requireNonNull(profile, "profile");
        if (!ammoRequired) {
            return true;
        }

        Container inventory = recruit.getInventory();
        int remaining = requiredAmmoUnits(profile, true);
        if (countAmmo(inventory, profile.ammoType()) < remaining) {
            return false;
        }

        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matches(stack, profile.ammoType())) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            } else {
                inventory.setItem(slot, stack);
            }
        }

        if (remaining != 0) {
            throw new IllegalStateException("ammo count changed during atomic consumption");
        }
        inventory.setChanged();
        return true;
    }

    public static int countAmmo(Container inventory, BoomstickAmmoType ammoType) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(ammoType, "ammoType");
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(stack, ammoType)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean matches(ItemStack stack, BoomstickAmmoType ammoType) {
        return ammoType == BoomstickAmmoType.ROUND_BALL
                ? SupportedBoomsticks.isRoundBallAmmo(stack)
                : SupportedBoomsticks.isHeavyBoltAmmo(stack);
    }
}
