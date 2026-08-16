package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.world.item.ItemStack;

/**
 * The wind-up marker a throwing weapon carries while a recruit holds it cocked.
 *
 * <p>It lives on the weapon stack rather than on the recruit, because the client renders the pose
 * from the held item and equipment stacks are already tracked to it.</p>
 *
 * <p>Vanilla would drive this through {@code startUsingItem}, but a recruit must not enter the
 * native item use path: these items throw from {@code finishUsingItem} or {@code releaseUsing}, so
 * a completed vanilla use would run the native throw a second time and spend another item.</p>
 */
final class BoomstickAimMarker {
    private static final String AIMING_KEY = "recruits_use_boomsticks:throwable_aiming";

    private BoomstickAimMarker() {
    }

    static void set(ItemStack weapon, boolean aiming) {
        if (weapon == null || weapon.isEmpty()) {
            return;
        }
        if (aiming) {
            weapon.getOrCreateTag().putBoolean(AIMING_KEY, true);
            return;
        }
        if (weapon.hasTag()) {
            weapon.getTag().remove(AIMING_KEY);
            if (weapon.getTag().isEmpty()) {
                weapon.setTag(null);
            }
        }
    }

    static boolean isSet(ItemStack weapon) {
        return weapon != null
                && !weapon.isEmpty()
                && weapon.hasTag()
                && weapon.getTag().getBoolean(AIMING_KEY);
    }
}
