package org.iwoss.recruits_use_boomsticks.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BoomstickShotFacingTest {
    @Test
    void calculatesCardinalYawAtShotBoundary() {
        BoomstickShotFacing.Angles south = BoomstickShotFacing.angles(Vec3.ZERO, new Vec3(0.0D, 0.0D, 4.0D));
        BoomstickShotFacing.Angles east = BoomstickShotFacing.angles(Vec3.ZERO, new Vec3(4.0D, 0.0D, 0.0D));

        assertEquals(0.0F, south.yaw(), 0.001F);
        assertEquals(-90.0F, east.yaw(), 0.001F);
        assertEquals(0.0F, south.pitch(), 0.001F);
    }

    @Test
    void calculatesVerticalPitchAndRejectsZeroLengthAim() {
        BoomstickShotFacing.Angles upward = BoomstickShotFacing.angles(
                Vec3.ZERO,
                new Vec3(0.0D, 4.0D, 4.0D));

        assertEquals(-45.0F, upward.pitch(), 0.001F);
        assertFalse(BoomstickShotFacing.angles(Vec3.ZERO, Vec3.ZERO).valid());
    }
}
