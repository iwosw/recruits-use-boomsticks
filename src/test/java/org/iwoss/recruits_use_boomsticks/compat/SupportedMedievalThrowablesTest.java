package org.iwoss.recruits_use_boomsticks.compat;

import org.iwoss.recruits_use_boomsticks.compat.SupportedMedievalThrowables.Family;
import org.iwoss.recruits_use_boomsticks.compat.SupportedMedievalThrowables.MedievalThrowable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedMedievalThrowablesTest {
    @Test
    void exposesOnlyTheConfirmedThrowingWeapons() {
        assertEquals(
                Set.of(
                        SupportedMedievalThrowables.THROWING_KNIFE_ID,
                        SupportedMedievalThrowables.THROWING_AXE_ID,
                        SupportedMedievalThrowables.SMALL_ROCK_ID,
                        SupportedMedievalThrowables.LARGE_ROCK_ID,
                        SupportedMedievalThrowables.WAR_DART_ID,
                        SupportedMedievalThrowables.JAVELIN_ID),
                SupportedMedievalThrowables.supportedWeaponIds());
        // The melee and firearm families of the same mod stay outside this boundary.
        assertTrue(SupportedMedievalThrowables.profileFor(SupportedBoomsticks.HANDGONNE_ID).isEmpty());
        assertTrue(SupportedMedievalThrowables.profileFor(SupportedBoomsticks.ARBALEST_ID).isEmpty());
        assertTrue(SupportedMedievalThrowables.profileFor("medieval_boomsticks:morningstar").isEmpty());
        assertTrue(SupportedMedievalThrowables.profileFor("medieval_boomsticks:recurve_bow").isEmpty());
        assertTrue(SupportedMedievalThrowables.profileFor("").isEmpty());
    }

    @Test
    void keepsEveryNativeThrowBranchExact() {
        assertNativeBranch(
                SupportedMedievalThrowables.THROWING_KNIFE_ID, Family.KNIFE, 2.0D, 8.0D, 0);
        assertNativeBranch(
                SupportedMedievalThrowables.THROWING_AXE_ID, Family.AXE, 1.6D, 8.0D, 0);
        assertNativeBranch(
                SupportedMedievalThrowables.SMALL_ROCK_ID, Family.SMALL_ROCK, 2.5D, 4.0D, 10);
        assertNativeBranch(
                SupportedMedievalThrowables.LARGE_ROCK_ID, Family.LARGE_ROCK, 1.0D, 18.0D, 10);
        // ThrowableWardart.onHitEntity reads the javelin damage setting rather than a wardart one.
        assertNativeBranch(
                SupportedMedievalThrowables.WAR_DART_ID, Family.WAR_DART, 1.0D, 8.0D, 10);
        assertNativeBranch(
                SupportedMedievalThrowables.JAVELIN_ID, Family.JAVELIN, 2.5D, 8.0D, 10);
        assertEquals(10, SupportedMedievalThrowables.COOLDOWN_TICKS);
        assertEquals(1.0F, SupportedMedievalThrowables.INACCURACY);
        assertEquals(10, SupportedMedievalThrowables.TRIDENT_WIND_UP_TICKS);
    }

    @Test
    void namesOneDistinctNativeProjectileForEveryWeapon() {
        assertEquals(
                SupportedMedievalThrowables.throwables().size(),
                SupportedMedievalThrowables.throwables().values().stream()
                        .map(MedievalThrowable::projectileId)
                        .distinct()
                        .count());
        assertEquals(
                SupportedMedievalThrowables.throwables().size(),
                SupportedMedievalThrowables.throwables().values().stream()
                        .map(MedievalThrowable::family)
                        .distinct()
                        .count());
        for (MedievalThrowable nativeThrowable : SupportedMedievalThrowables.throwables().values()) {
            assertTrue(nativeThrowable.projectileId()
                    .startsWith(SupportedMedievalThrowables.MOD_ID + ":"));
            assertTrue(nativeThrowable.projectileClassName()
                    .startsWith("com.TBK.medieval_boomsticks.server.entity."));
        }
    }

    @Test
    void derivesEveryWeaponProfileFromItsNativeBranch() {
        for (MedievalThrowable nativeThrowable : SupportedMedievalThrowables.throwables().values()) {
            BoomstickWeaponProfile profile = nativeThrowable.profile();

            assertEquals(nativeThrowable.weaponId(), profile.registryId());
            assertEquals(BoomstickAmmoType.THROWN_WEAPON, profile.ammoType());
            assertEquals(1, profile.projectileCount());
            assertEquals(nativeThrowable.defaultVelocity(), profile.projectileVelocity());
            assertEquals(nativeThrowable.inaccuracy(), profile.inaccuracy());
            assertEquals(nativeThrowable.throwSound(), profile.firingSound());
            assertNotEquals(BoomstickSound.NONE, profile.firingSound());
        }
    }

    @Test
    void prefersALiveConfiguredSpeedButNeverAnUnusableOne() {
        MedievalThrowable largeRock = SupportedMedievalThrowables
                .throwableFor(SupportedMedievalThrowables.LARGE_ROCK_ID)
                .orElseThrow();

        assertEquals(1.75D, largeRock.profile(1.75D).projectileVelocity());
        // Medieval Boomsticks allows a speed of zero, and nothing can be thrown at it.
        assertEquals(largeRock.defaultVelocity(), largeRock.profile(0.0D).projectileVelocity());
        assertEquals(largeRock.defaultVelocity(), largeRock.profile(-1.0D).projectileVelocity());
        assertEquals(largeRock.defaultVelocity(), largeRock.profile(Double.NaN).projectileVelocity());
        assertEquals(2.0D, MedievalThrowable.usableOr(2.0D, 9.0D));
        assertEquals(9.0D, MedievalThrowable.usableOr(Double.POSITIVE_INFINITY, 9.0D));
    }

    @Test
    void rejectsAnIncompleteThrowingWeapon() {
        assertThrows(IllegalArgumentException.class, () -> new MedievalThrowable(
                " ", "id", "Class", Family.KNIFE, 1.0D, 1.0D, 0.0F, 0, BoomstickSound.THROW_WEAPON));
        assertThrows(IllegalArgumentException.class, () -> new MedievalThrowable(
                "id", "", "Class", Family.KNIFE, 1.0D, 1.0D, 0.0F, 0, BoomstickSound.THROW_WEAPON));
        assertThrows(IllegalArgumentException.class, () -> new MedievalThrowable(
                "id", "id", "Class", Family.KNIFE, 0.0D, 1.0D, 0.0F, 0, BoomstickSound.THROW_WEAPON));
        assertThrows(IllegalArgumentException.class, () -> new MedievalThrowable(
                "id", "id", "Class", Family.KNIFE, 1.0D, 0.0D, 0.0F, 0, BoomstickSound.THROW_WEAPON));
        assertThrows(IllegalArgumentException.class, () -> new MedievalThrowable(
                "id", "id", "Class", Family.KNIFE, 1.0D, 1.0D, -1.0F, 0, BoomstickSound.THROW_WEAPON));
        assertThrows(IllegalArgumentException.class, () -> new MedievalThrowable(
                "id", "id", "Class", Family.KNIFE, 1.0D, 1.0D, 0.0F, -1, BoomstickSound.THROW_WEAPON));
        assertThrows(NullPointerException.class, () -> new MedievalThrowable(
                "id", "id", "Class", null, 1.0D, 1.0D, 0.0F, 0, BoomstickSound.THROW_WEAPON));
        assertThrows(NullPointerException.class, () -> new MedievalThrowable(
                "id", "id", "Class", Family.KNIFE, 1.0D, 1.0D, 0.0F, 0, null));
    }

    @Test
    void reachMatchesEachProjectileSpeed() {
        double sharedCombatRange = 45.0D;
        double largeRockReach = BoomstickBallistics.maxCompensatedRange(
                SupportedMedievalThrowables.throwableFor(SupportedMedievalThrowables.LARGE_ROCK_ID)
                        .orElseThrow()
                        .defaultVelocity());
        double javelinReach = BoomstickBallistics.maxCompensatedRange(
                SupportedMedievalThrowables.throwableFor(SupportedMedievalThrowables.JAVELIN_ID)
                        .orElseThrow()
                        .defaultVelocity());

        // No Medieval Boomsticks throwable outruns the shared combat range: a large rock at velocity
        // 1.0 runs out of drop compensation after roughly eighteen blocks, and even the fastest of
        // them, the javelin at 2.5, falls just short of the full range, so the goal always has a
        // distance to close rather than lobbing shots into the ground.
        assertTrue(largeRockReach > 0.0D && largeRockReach < sharedCombatRange);
        assertTrue(javelinReach < sharedCombatRange);
        assertTrue(javelinReach > 2.0D * largeRockReach);
    }

    private static void assertNativeBranch(
            String weaponId,
            Family family,
            double velocity,
            double damage,
            int windUpTicks
    ) {
        MedievalThrowable nativeThrowable = SupportedMedievalThrowables
                .throwableFor(weaponId)
                .orElseThrow();

        assertEquals(weaponId, nativeThrowable.weaponId());
        assertEquals(family, nativeThrowable.family());
        assertEquals(velocity, nativeThrowable.defaultVelocity());
        assertEquals(damage, nativeThrowable.defaultDamage());
        assertEquals(SupportedMedievalThrowables.INACCURACY, nativeThrowable.inaccuracy());
        assertEquals(windUpTicks, nativeThrowable.windUpTicks());
    }
}
