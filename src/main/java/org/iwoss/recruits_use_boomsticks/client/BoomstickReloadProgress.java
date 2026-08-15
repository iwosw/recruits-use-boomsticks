package org.iwoss.recruits_use_boomsticks.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side stopwatch for a recruit's reload, kept for the renderer alone.
 *
 * <p>The reload window lives in the combat goal on the server and nothing about its progress is
 * sent to clients. What is sent is the weapon's own reloading marker, so the renderer starts its
 * own clock the first frame it sees that marker and stops when the marker clears.</p>
 */
public final class BoomstickReloadProgress {
    private static final Map<Integer, Float> STARTED_AT = new HashMap<>();

    private BoomstickReloadProgress() {
    }

    /**
     * Returns where in a repeating pull of {@code cycleTicks} the reload currently stands, from 0 at
     * the start of a pull to just under 1 at its end.
     *
     * <p>The pull repeats rather than running once because the window it has to cover is not known
     * here, and is not a fixed number in the first place: it is however long the combat goal keeps
     * the weapon's reloading marker raised, which spans several of its phases. A single pull stretched
     * over a guessed length finishes early and leaves the arms frozen for the remainder, so instead
     * the recruit keeps working the weapon for exactly as long as the marker stands.</p>
     *
     * @param ageInTicks the renderer's own clock, which already carries the partial tick and so
     *                   keeps the motion smooth between ticks
     */
    public static float progress(int entityId, float ageInTicks, int cycleTicks) {
        float start = STARTED_AT.computeIfAbsent(entityId, id -> ageInTicks);
        if (ageInTicks < start) {
            // The entity's own age only ever grows, so this means a respawned id reusing the slot.
            STARTED_AT.put(entityId, ageInTicks);
            return 0.0F;
        }
        float cycle = Math.max(1, cycleTicks);
        return ((ageInTicks - start) % cycle) / cycle;
    }

    /** Forgets a recruit's clock so its next reload starts from zero. */
    public static void clear(int entityId) {
        STARTED_AT.remove(entityId);
    }

    /** Entity ids are only unique per connection, so nothing may survive a disconnect. */
    public static void clearAll() {
        STARTED_AT.clear();
    }
}
