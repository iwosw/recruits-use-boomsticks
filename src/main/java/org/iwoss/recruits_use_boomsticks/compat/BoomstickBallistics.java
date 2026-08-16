package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * Launch geometry shared by every adapter that spawns an {@link AbstractArrow} itself.
 *
 * <p>The numbers belong to the vanilla projectile rather than to any one integration, so both the
 * Artillery and the Medieval Boomsticks families aim through the same arc.</p>
 */
public final class BoomstickBallistics {
    /** Per-tick gravity {@link AbstractArrow} applies to its own motion. */
    private static final double ARROW_GRAVITY_PER_TICK = 0.05D;
    /** Longest lead this policy will add, so a hopeless long shot cannot aim at the sky. */
    private static final double MAX_AIM_ARC = 8.0D;

    private BoomstickBallistics() {
    }

    /**
     * Builds the launch vector, adding the upward arc a projectile needs to reach its target.
     *
     * <p>Recruits aim at a third of the target's height, below their own eyes, and every projectile
     * here extends {@link AbstractArrow} and therefore falls at {@value #ARROW_GRAVITY_PER_TICK}
     * blocks per tick squared. Without a lead the shot lands visibly short and low.</p>
     *
     * <p>{@code shoot} treats velocity as blocks per tick, so flight time is roughly the horizontal
     * distance divided by that velocity, and the drop over that flight is {@code g/2 * t^2}. Adding
     * exactly that drop back is self-calibrating: a fast iron ball barely arcs while a slow bolt
     * gets a real lob. Drag makes the true flight slightly longer, so this stays a mild
     * under-compensation rather than an overshoot.</p>
     */
    public static Vec3 aimVector(Vec3 origin, Vec3 target, double projectileVelocity) {
        double dx = target.x - origin.x;
        double dy = target.y - origin.y;
        double dz = target.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= 0.0D || !Double.isFinite(projectileVelocity) || projectileVelocity <= 0.0D) {
            return new Vec3(dx, dy, dz);
        }
        double flightTicks = horizontal / projectileVelocity;
        double arc = Math.min(MAX_AIM_ARC, 0.5D * ARROW_GRAVITY_PER_TICK * flightTicks * flightTicks);
        return new Vec3(dx, dy + arc, dz);
    }

    /**
     * Horizontal distance past which {@link #aimVector} can no longer pay for the drop.
     *
     * <p>This is the inverse of the arc above at its cap: the compensation grows with the square of
     * the flight time until it hits {@link #MAX_AIM_ARC}, and beyond that point the lead is clipped
     * and the projectile lands short no matter how long the recruit aims. A slow projectile reaches
     * that wall early — a thrown cobblestone at velocity {@code 0.75} runs out at roughly thirteen
     * blocks — so the combat goal uses this to decide when to close the distance instead of lobbing
     * shots into the ground.</p>
     */
    public static double maxCompensatedRange(double projectileVelocity) {
        if (!Double.isFinite(projectileVelocity) || projectileVelocity <= 0.0D) {
            return 0.0D;
        }
        return projectileVelocity * Math.sqrt(2.0D * MAX_AIM_ARC / ARROW_GRAVITY_PER_TICK);
    }
}
