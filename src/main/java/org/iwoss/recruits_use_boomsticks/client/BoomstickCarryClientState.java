package org.iwoss.recruits_use_boomsticks.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side record of which recruits are carrying their firearm on order.
 *
 * <p>Only the renderer reads this. The order itself lives on the server; this exists so the parade
 * carry can be shown for a loaded weapon, which would otherwise render in the firing pose.</p>
 */
public final class BoomstickCarryClientState {
    private static final Set<Integer> CARRYING = new HashSet<>();

    private BoomstickCarryClientState() {
    }

    public static void set(int entityId, boolean carrying) {
        if (carrying) {
            CARRYING.add(entityId);
        } else {
            CARRYING.remove(entityId);
        }
    }

    public static boolean isCarrying(int entityId) {
        return CARRYING.contains(entityId);
    }

    /** Entity ids are only unique per connection, so nothing may survive a disconnect. */
    public static void clear() {
        CARRYING.clear();
    }
}
