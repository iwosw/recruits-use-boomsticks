package org.iwoss.recruits_use_boomsticks.ai;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Reapplies authorized throwing aim after late formation steering has finished for the tick. */
public final class BoomstickThrowingAimFacing {
    private static final Map<UUID, LockedAim> LOCKED_AIMS = new HashMap<>();

    private BoomstickThrowingAimFacing() {
    }

    public static void track(CrossBowmanEntity recruit, Vec3 targetPosition, UUID targetId) {
        if (recruit == null
                || targetPosition == null
                || recruit.level().isClientSide
                || !recruit.isAlive()) {
            return;
        }
        LOCKED_AIMS.put(recruit.getUUID(), new LockedAim(recruit, targetPosition, targetId));
    }

    public static void untrack(CrossBowmanEntity recruit) {
        if (recruit != null) {
            LOCKED_AIMS.remove(recruit.getUUID());
        }
    }

    public static boolean isTracked(CrossBowmanEntity recruit) {
        return recruit != null && LOCKED_AIMS.containsKey(recruit.getUUID());
    }

    public static void clearTarget(UUID targetId) {
        if (targetId != null) {
            LOCKED_AIMS.values().removeIf(aim -> targetId.equals(aim.targetId()));
        }
    }

    public static void applyAfterFormations() {
        for (LockedAim aim : LOCKED_AIMS.values()) {
            if (aim.recruit().isAlive() && !aim.recruit().level().isClientSide) {
                BoomstickShotFacing.face(aim.recruit(), aim.targetPosition());
            }
        }
        // An active aim is submitted again by its combat goal next tick. Clearing here prevents a
        // stopped or denied goal from retaining a recruit or forcing one additional stale rotation.
        LOCKED_AIMS.clear();
    }

    public static void clear() {
        LOCKED_AIMS.clear();
    }

    private record LockedAim(CrossBowmanEntity recruit, Vec3 targetPosition, UUID targetId) {
    }
}
