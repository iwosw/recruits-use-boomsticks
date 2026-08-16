package org.iwoss.recruits_use_boomsticks.ai;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side facing correction that overrides whatever else steered the recruit this tick.
 *
 * <p>It is written at the shot boundary for every weapon, and additionally on every tick of a
 * throwing wind-up, where the raised arm has to stay aligned with the target rather than with the
 * formation. See {@link BoomstickThrowingAimFacing} for the second case.</p>
 */
final class BoomstickShotFacing {
    private BoomstickShotFacing() {
    }

    static void face(CrossBowmanEntity recruit, Vec3 targetPosition) {
        if (recruit == null || targetPosition == null) {
            return;
        }
        Angles angles = angles(recruit.getEyePosition(1.0F), targetPosition);
        if (!angles.valid()) {
            return;
        }

        // Formation goals can rewrite body yaw after LookControl has started turning. Write all
        // three rotations together immediately before spawning the projectile, so the visible body,
        // head, and actual shot direction agree for this server tick.
        recruit.getLookControl().setLookAt(
                targetPosition.x,
                targetPosition.y,
                targetPosition.z,
                360.0F,
                360.0F);
        apply(recruit, angles.yaw(), angles.pitch(), angles.yaw(), angles.yaw());
    }

    static Snapshot capture(CrossBowmanEntity recruit) {
        return recruit == null
                ? null
                : new Snapshot(
                        recruit.getYRot(),
                        recruit.getXRot(),
                        recruit.getYHeadRot(),
                        recruit.yBodyRot);
    }

    static void restore(CrossBowmanEntity recruit, Snapshot snapshot) {
        if (recruit == null || snapshot == null) {
            return;
        }
        apply(
                recruit,
                snapshot.yaw(),
                snapshot.pitch(),
                snapshot.headYaw(),
                snapshot.bodyYaw());

        // Replace LookControl's old target with a point straight ahead in the saved formation
        // direction. Otherwise it keeps turning the head back toward the enemy one tick after the
        // explicit rotation was restored.
        Vec3 eye = recruit.getEyePosition(1.0F);
        Vec3 forward = Vec3.directionFromRotation(snapshot.pitch(), snapshot.yaw());
        Vec3 lookPoint = eye.add(forward.scale(4.0D));
        recruit.getLookControl().setLookAt(
                lookPoint.x,
                lookPoint.y,
                lookPoint.z,
                360.0F,
                360.0F);
    }

    private static void apply(
            CrossBowmanEntity recruit,
            float yaw,
            float pitch,
            float headYaw,
            float bodyYaw
    ) {
        recruit.setYRot(yaw);
        recruit.setXRot(pitch);
        recruit.setYHeadRot(headYaw);
        recruit.setYBodyRot(bodyYaw);
        // Avoid interpolating through a visible spin when the client receives the snap at either
        // side of the shot animation.
        recruit.yRotO = yaw;
        recruit.xRotO = pitch;
        recruit.yHeadRotO = headYaw;
        recruit.yBodyRotO = bodyYaw;
    }

    static Angles angles(Vec3 origin, Vec3 target) {
        if (origin == null || target == null) {
            return Angles.INVALID;
        }
        double dx = target.x - origin.x;
        double dy = target.y - origin.y;
        double dz = target.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (!Double.isFinite(horizontal)
                || !Double.isFinite(dy)
                || horizontal * horizontal + dy * dy < 1.0E-8D) {
            return Angles.INVALID;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG));
        return new Angles(yaw, pitch, Float.isFinite(yaw) && Float.isFinite(pitch));
    }

    record Angles(float yaw, float pitch, boolean valid) {
        private static final Angles INVALID = new Angles(0.0F, 0.0F, false);
    }

    record Snapshot(float yaw, float pitch, float headYaw, float bodyYaw) {
    }
}
