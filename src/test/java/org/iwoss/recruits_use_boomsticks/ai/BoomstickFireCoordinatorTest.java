package org.iwoss.recruits_use_boomsticks.ai;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoomstickFireCoordinatorTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SHOOTER_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SHOOTER_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void oneLethalReservationBlocksTheRestOfTheFormation() {
        BoomstickFireCoordinator coordinator = new BoomstickFireCoordinator();

        assertTrue(coordinator.reserve(SHOOTER_ONE, TARGET, 20.0D, 24.0D, 1, 100L, 5));
        assertFalse(coordinator.reserve(SHOOTER_TWO, TARGET, 20.0D, 24.0D, 1, 100L, 5));
        assertEquals(1, coordinator.reservationCount(TARGET, 100L));
    }

    @Test
    void tougherTargetAllowsOnlyTheNumberOfVolleysItNeeds() {
        BoomstickFireCoordinator coordinator = new BoomstickFireCoordinator();
        UUID shooterThree = UUID.fromString("00000000-0000-0000-0000-000000000003");

        assertTrue(coordinator.reserve(SHOOTER_ONE, TARGET, 20.0D, 8.0D, 1, 100L, 5));
        assertTrue(coordinator.reserve(SHOOTER_TWO, TARGET, 20.0D, 8.0D, 1, 100L, 5));
        assertTrue(coordinator.reserve(shooterThree, TARGET, 20.0D, 8.0D, 1, 100L, 5));
        assertFalse(coordinator.reserve(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                TARGET,
                20.0D,
                8.0D,
                1,
                100L,
                5));
    }

    @Test
    void formationNeverCommitsMoreThanTenPhysicalProjectiles() {
        BoomstickFireCoordinator coordinator = new BoomstickFireCoordinator();
        for (int index = 0; index < 10; index++) {
            assertTrue(coordinator.reserve(
                    new UUID(0L, index + 1L),
                    TARGET,
                    1_000.0D,
                    1.0D,
                    1,
                    100L,
                    5));
        }
        assertFalse(coordinator.reserve(new UUID(0L, 11L), TARGET, 1_000.0D, 1.0D, 1, 100L, 5));
    }

    @Test
    void committedShotSurvivesGoalTeardownUntilImpactOrExpiry() {
        BoomstickFireCoordinator coordinator = new BoomstickFireCoordinator();

        assertTrue(coordinator.reserve(SHOOTER_ONE, TARGET, 20.0D, 20.0D, 1, 100L, 5));
        coordinator.commit(SHOOTER_ONE, TARGET, 100L, 40);
        assertEquals(1, coordinator.reservationCount(TARGET, 139L));
        assertEquals(0, coordinator.reservationCount(TARGET, 140L));
    }

    @Test
    void impactKeepsReservationThroughCurrentTickOnly() {
        BoomstickFireCoordinator coordinator = new BoomstickFireCoordinator();

        assertTrue(coordinator.reserve(SHOOTER_ONE, TARGET, 20.0D, 20.0D, 1, 100L, 5));
        coordinator.commit(SHOOTER_ONE, TARGET, 100L, 40);
        coordinator.resolveHit(SHOOTER_ONE, TARGET, 110L);

        assertEquals(1, coordinator.reservationCount(TARGET, 110L));
        assertEquals(0, coordinator.reservationCount(TARGET, 111L));
    }
}
