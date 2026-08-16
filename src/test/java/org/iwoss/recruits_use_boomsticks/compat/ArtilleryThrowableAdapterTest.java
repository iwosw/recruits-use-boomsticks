package org.iwoss.recruits_use_boomsticks.compat;

import org.iwoss.recruits_use_boomsticks.compat.SupportedArtilleryThrowables.ArtilleryThrowable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtilleryThrowableAdapterTest {
    @Test
    void exposesOnlyTheConfirmedThrowingWeapons() {
        assertEquals(
                Set.of(
                        SupportedArtilleryThrowables.FRANCISCA_ID,
                        SupportedArtilleryThrowables.HURLBAT_ID,
                        SupportedArtilleryThrowables.THROWING_CROSS_ID,
                        SupportedArtilleryThrowables.JAVELIN_ID,
                        SupportedArtilleryThrowables.THROWABLE_COBBLESTONE_ID),
                SupportedArtilleryThrowables.supportedWeaponIds());
        assertTrue(SupportedArtilleryThrowables
                .profileFor("artillery_addon:clay_hand_grenade")
                .isEmpty());
        assertTrue(SupportedArtilleryThrowables
                .profileFor("artillery_addon:iron_hand_grenade")
                .isEmpty());
        assertTrue(SupportedArtilleryThrowables
                .profileFor("artillery_addon:fire_bomb")
                .isEmpty());
        assertTrue(SupportedArtilleryThrowables
                .profileFor("artillery_addon:lime_bomb")
                .isEmpty());
        assertTrue(SupportedArtilleryThrowables.profileFor("artillery_addon:arquebus").isEmpty());
        assertTrue(SupportedArtilleryThrowables.profileFor("").isEmpty());
    }

    @Test
    void keepsEveryNativeFullUseBranchExact() {
        assertNativeBranch(SupportedArtilleryThrowables.FRANCISCA_ID, 15, 5.5D, 1.5D, 1.9F, 0.7D);
        assertNativeBranch(SupportedArtilleryThrowables.HURLBAT_ID, 14, 6.5D, 1.2D, 2.0F, 0.4D);
        assertNativeBranch(SupportedArtilleryThrowables.THROWING_CROSS_ID, 13, 4.5D, 1.4D, 1.7F, 0.4D);
        assertNativeBranch(SupportedArtilleryThrowables.JAVELIN_ID, 40, 2.0D, 3.5D, 2.2F, 0.75D);
        // ThrowcobbleEntity has no native block-impact recovery procedure in either artifact.
        assertNativeBranch(
                SupportedArtilleryThrowables.THROWABLE_COBBLESTONE_ID, 30, 15.0D, 0.75D, 0.0F, 0.0D);
        assertEquals(10, SupportedArtilleryThrowables.COOLDOWN_TICKS);
        assertEquals(1, SupportedArtilleryThrowables.KNOCKBACK);
    }

    @Test
    void derivesEveryWeaponProfileFromItsNativeBranch() {
        for (ArtilleryThrowable nativeThrowable : SupportedArtilleryThrowables.throwables().values()) {
            BoomstickWeaponProfile profile = nativeThrowable.profile();

            assertEquals(nativeThrowable.weaponId(), profile.registryId());
            assertEquals(BoomstickAmmoType.THROWN_WEAPON, profile.ammoType());
            assertEquals(1, profile.projectileCount());
            assertEquals(nativeThrowable.velocity(), profile.projectileVelocity());
            assertEquals(nativeThrowable.inaccuracy(), profile.inaccuracy());
            assertEquals(BoomstickSound.NONE, profile.firingSound());
        }
    }

    @Test
    void rejectsThrowablesThatDoNotDescribeANativeBranch() {
        assertThrows(IllegalArgumentException.class, () -> new ArtilleryThrowable(
                " ", "a:b", "C", 15, 5.5D, 1.5D, 1.9F, 0.7D));
        assertThrows(IllegalArgumentException.class, () -> new ArtilleryThrowable(
                "a:b", "a:c", "C", 0, 5.5D, 1.5D, 1.9F, 0.7D));
        assertThrows(IllegalArgumentException.class, () -> new ArtilleryThrowable(
                "a:b", "a:c", "C", 15, 5.5D, 0.0D, 1.9F, 0.7D));
        assertThrows(IllegalArgumentException.class, () -> new ArtilleryThrowable(
                "a:b", "a:c", "C", 15, 5.5D, 1.5D, -1.0F, 0.7D));
        assertThrows(IllegalArgumentException.class, () -> new ArtilleryThrowable(
                "a:b", "a:c", "C", 15, 5.5D, 1.5D, 1.9F, 1.5D));
    }

    @Test
    void reachIsBoundedByTheDropCompensationEachVelocityCanPayFor() {
        // aimVector clips its lead at 8 blocks of arc under 0.05 blocks/tick^2 of gravity, so the
        // reach is velocity * sqrt(2 * 8 / 0.05) — the point past which a shot must land short.
        double perVelocity = Math.sqrt(2.0D * 8.0D / 0.05D);

        assertEquals(0.75D * perVelocity, ArtilleryAddonAdapter.maxCompensatedRange(0.75D), 1.0E-9D);
        assertEquals(1.5D * perVelocity, ArtilleryAddonAdapter.maxCompensatedRange(1.5D), 1.0E-9D);
        // The cobblestone runs out well inside the shared 45-block combat range; the javelin does not.
        assertTrue(ArtilleryAddonAdapter.maxCompensatedRange(0.75D) < 45.0D);
        assertTrue(ArtilleryAddonAdapter.maxCompensatedRange(3.5D) > 45.0D);
        assertEquals(0.0D, ArtilleryAddonAdapter.maxCompensatedRange(0.0D));
        assertEquals(0.0D, ArtilleryAddonAdapter.maxCompensatedRange(Double.NaN));
    }

    @Test
    void anUnclaimedStackKeepsTheSharedRangeAndNeverWindsUp() {
        ArtilleryThrowableAdapter available = new ArtilleryThrowableAdapter(() -> true);

        assertEquals(45.0D, available.effectiveRange(null, 45.0D));
        assertFalse(available.isAiming(null));
    }

    @Test
    void keepsAvailabilityAndNativeProjectileIdentityExplicit() {
        ArtilleryThrowableAdapter unavailable = new ArtilleryThrowableAdapter(() -> false);
        ArtilleryThrowableAdapter available = new ArtilleryThrowableAdapter(() -> true);

        assertFalse(unavailable.isAvailable());
        assertTrue(available.isAvailable());
        assertEquals(RecruitWeaponIntegration.ARTILLERY_ADDON, available.integration());

        for (ArtilleryThrowable nativeThrowable : SupportedArtilleryThrowables.throwables().values()) {
            assertTrue(available.supportsProjectileClassName(nativeThrowable.projectileClassName()));
            assertFalse(unavailable.supportsProjectileClassName(
                    nativeThrowable.projectileClassName()));
        }
        assertFalse(available.supportsProjectileClassName(
                "net.mcreator.artilleryaddon.entity.ClaynadeProEntity"));
        assertFalse(available.supportsProjectileClassName(String.class.getName()));
        assertFalse(available.supportsProjectileClassName(null));
        assertFalse(available.supportsAmmo(null));
        assertFalse(available.isThrowable(null));
        // Artillery's throwables run a native finish path, so a recruit must never be put into the
        // vanilla use state for them: a completed use would throw a second time at no cost.
        assertFalse(available.windUpUsesNativeItemState(null));
    }

    private static void assertNativeBranch(
            String weaponId,
            int useDurationTicks,
            double baseDamage,
            double velocity,
            float inaccuracy,
            double impactRecoveryChance
    ) {
        ArtilleryThrowable nativeThrowable = SupportedArtilleryThrowables
                .throwableFor(weaponId)
                .orElseThrow();

        assertEquals(useDurationTicks, nativeThrowable.useDurationTicks(), weaponId);
        assertEquals(baseDamage, nativeThrowable.baseDamage(), weaponId);
        assertEquals(velocity, nativeThrowable.velocity(), weaponId);
        assertEquals(inaccuracy, nativeThrowable.inaccuracy(), weaponId);
        assertEquals(impactRecoveryChance, nativeThrowable.impactRecoveryChance(), weaponId);
    }
}
