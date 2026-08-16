package org.iwoss.recruits_use_boomsticks.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side reservations for formation fire against one living target.
 *
 * <p>An aiming recruit reserves its expected volley damage. Later recruits may aim only while the
 * already-reserved damage is still below the target's current survivable health, and no formation
 * may commit more than ten physical projectiles at once. A committed reservation stays alive while
 * its projectile is in flight, so a second rank does not fire into a target that is already dead in
 * practice but has not received the first projectile yet.</p>
 *
 * <p>Self defence overrides the overkill limit. A recruit the target is itself fighting always gets
 * a firing place, even when the formation has already reserved lethal damage, so a recruit is never
 * left standing still while the enemy chews on it. The ten-projectile ceiling still applies.</p>
 */
public final class BoomstickFireCoordinator {
    static final int MAX_COMMITTED_PROJECTILES = 10;
    private static final BoomstickFireCoordinator SHARED = new BoomstickFireCoordinator();

    private final Map<UUID, Map<UUID, Reservation>> reservationsByTarget = new HashMap<>();

    public static boolean reserveShared(
            UUID shooterId,
            UUID targetId,
            double targetSurvivableHealth,
            double expectedDamage,
            int projectileCount,
            long gameTime,
            int leaseTicks
    ) {
        return reserveShared(
                shooterId,
                targetId,
                targetSurvivableHealth,
                expectedDamage,
                projectileCount,
                gameTime,
                leaseTicks,
                false);
    }

    public static boolean reserveShared(
            UUID shooterId,
            UUID targetId,
            double targetSurvivableHealth,
            double expectedDamage,
            int projectileCount,
            long gameTime,
            int leaseTicks,
            boolean selfDefense
    ) {
        return SHARED.reserve(
                shooterId,
                targetId,
                targetSurvivableHealth,
                expectedDamage,
                projectileCount,
                gameTime,
                leaseTicks,
                selfDefense);
    }

    public static boolean hasFireTurnCapacityShared(
            UUID targetId,
            double targetSurvivableHealth,
            int projectileCount,
            long gameTime
    ) {
        return SHARED.hasFireTurnCapacity(targetId, targetSurvivableHealth, projectileCount, gameTime);
    }

    public static void commitShared(UUID shooterId, UUID targetId, long gameTime, int flightTicks) {
        SHARED.commit(shooterId, targetId, gameTime, flightTicks);
    }

    public static void releaseShared(UUID shooterId, UUID targetId) {
        SHARED.release(shooterId, targetId);
    }

    public static void resolveHitShared(UUID shooterId, UUID targetId, long gameTime) {
        SHARED.resolveHit(shooterId, targetId, gameTime);
    }

    public static void clearTargetShared(UUID targetId) {
        SHARED.clearTarget(targetId);
    }

    public static void clearShared() {
        SHARED.clear();
    }

    boolean reserve(
            UUID shooterId,
            UUID targetId,
            double targetSurvivableHealth,
            double expectedDamage,
            int projectileCount,
            long gameTime,
            int leaseTicks
    ) {
        return reserve(
                shooterId,
                targetId,
                targetSurvivableHealth,
                expectedDamage,
                projectileCount,
                gameTime,
                leaseTicks,
                false);
    }

    boolean reserve(
            UUID shooterId,
            UUID targetId,
            double targetSurvivableHealth,
            double expectedDamage,
            int projectileCount,
            long gameTime,
            int leaseTicks,
            boolean selfDefense
    ) {
        if (shooterId == null || targetId == null || shooterId.equals(targetId)) {
            return false;
        }
        purgeExpired(gameTime);

        Map<UUID, Reservation> targetReservations =
                reservationsByTarget.computeIfAbsent(targetId, ignored -> new HashMap<>());
        Reservation existing = targetReservations.get(shooterId);
        if (existing != null) {
            if (existing.committed) {
                return false;
            }
            existing.expectedDamage = positive(expectedDamage);
            existing.projectileCount = validProjectileCount(projectileCount);
            existing.touchedAt = gameTime;
            existing.expiresAt = safeExpiry(gameTime, leaseTicks);
            return true;
        }

        Load load = load(targetReservations);
        double survivableHealth = positive(targetSurvivableHealth);
        int requestedProjectiles = validProjectileCount(projectileCount);
        boolean overkill = !selfDefense && load.damage >= survivableHealth;
        if (overkill
                || load.projectiles + requestedProjectiles > MAX_COMMITTED_PROJECTILES) {
            if (targetReservations.isEmpty()) {
                reservationsByTarget.remove(targetId);
            }
            return false;
        }

        targetReservations.put(
                shooterId,
                new Reservation(
                        positive(expectedDamage),
                        requestedProjectiles,
                        gameTime,
                        safeExpiry(gameTime, leaseTicks),
                        false));
        return true;
    }

    /**
     * Whether a fresh shooter would still be granted a firing place against this target.
     *
     * <p>Read-only counterpart of {@link #reserve}, used by a blocked recruit to find an enemy that
     * still needs shooting instead of standing idle behind a kill its comrades already own. It is a
     * map lookup over the reservations of one target, so it stays cheap enough to ask about several
     * candidates in the same tick.</p>
     */
    boolean hasFireTurnCapacity(
            UUID targetId,
            double targetSurvivableHealth,
            int projectileCount,
            long gameTime
    ) {
        if (targetId == null) {
            return false;
        }
        purgeExpired(gameTime);
        Map<UUID, Reservation> targetReservations = reservationsByTarget.get(targetId);
        if (targetReservations == null) {
            return true;
        }
        Load load = load(targetReservations);
        return load.damage < positive(targetSurvivableHealth)
                && load.projectiles + validProjectileCount(projectileCount) <= MAX_COMMITTED_PROJECTILES;
    }

    void commit(UUID shooterId, UUID targetId, long gameTime, int flightTicks) {
        Reservation reservation = reservation(shooterId, targetId);
        if (reservation == null) {
            return;
        }
        reservation.committed = true;
        reservation.touchedAt = gameTime;
        reservation.expiresAt = safeExpiry(gameTime, flightTicks);
    }

    void release(UUID shooterId, UUID targetId) {
        Map<UUID, Reservation> targetReservations = reservationsByTarget.get(targetId);
        if (targetReservations == null) {
            return;
        }
        targetReservations.remove(shooterId);
        if (targetReservations.isEmpty()) {
            reservationsByTarget.remove(targetId);
        }
    }

    void resolveHit(UUID shooterId, UUID targetId, long gameTime) {
        Reservation reservation = reservation(shooterId, targetId);
        if (reservation == null) {
            return;
        }
        // LivingHurtEvent runs before the health subtraction. Keep the reservation through the rest
        // of this tick, then let the next AI tick see the target's updated health.
        reservation.touchedAt = gameTime;
        reservation.expiresAt = safeExpiry(gameTime, 1);
    }

    void clearTarget(UUID targetId) {
        if (targetId != null) {
            reservationsByTarget.remove(targetId);
        }
    }

    void clear() {
        reservationsByTarget.clear();
    }

    int reservationCount(UUID targetId, long gameTime) {
        purgeExpired(gameTime);
        Map<UUID, Reservation> targetReservations = reservationsByTarget.get(targetId);
        return targetReservations == null ? 0 : targetReservations.size();
    }

    private Reservation reservation(UUID shooterId, UUID targetId) {
        Map<UUID, Reservation> targetReservations = reservationsByTarget.get(targetId);
        return targetReservations == null ? null : targetReservations.get(shooterId);
    }

    private void purgeExpired(long gameTime) {
        Iterator<Map.Entry<UUID, Map<UUID, Reservation>>> targets =
                reservationsByTarget.entrySet().iterator();
        while (targets.hasNext()) {
            Map<UUID, Reservation> targetReservations = targets.next().getValue();
            targetReservations.values().removeIf(reservation ->
                    gameTime < reservation.touchedAt || reservation.expiresAt <= gameTime);
            if (targetReservations.isEmpty()) {
                targets.remove();
            }
        }
    }

    private static Load load(Map<UUID, Reservation> targetReservations) {
        double damage = 0.0D;
        int projectiles = 0;
        for (Reservation reservation : targetReservations.values()) {
            damage += reservation.expectedDamage;
            projectiles += reservation.projectileCount;
        }
        return new Load(damage, projectiles);
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 1.0D;
    }

    private static int validProjectileCount(int value) {
        return Math.max(1, value);
    }

    private static long safeExpiry(long gameTime, int ticks) {
        long duration = Math.max(1, ticks);
        return gameTime > Long.MAX_VALUE - duration ? Long.MAX_VALUE : gameTime + duration;
    }

    /** Damage and physical projectiles already claimed against one target. */
    private record Load(double damage, int projectiles) {
    }

    private static final class Reservation {
        private double expectedDamage;
        private int projectileCount;
        private long touchedAt;
        private long expiresAt;
        private boolean committed;

        private Reservation(
                double expectedDamage,
                int projectileCount,
                long touchedAt,
                long expiresAt,
                boolean committed
        ) {
            this.expectedDamage = expectedDamage;
            this.projectileCount = projectileCount;
            this.touchedAt = touchedAt;
            this.expiresAt = expiresAt;
            this.committed = committed;
        }
    }
}
