package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecruitWeaponAdaptersTest {
    @Test
    void returnsTheOnlyAdapterThatClaimsAWeapon() {
        TestAdapter first = new TestAdapter();
        TestAdapter second = new TestAdapter();
        RecruitWeaponAdapters adapters = new RecruitWeaponAdapters(List.of(first, second));

        assertSame(second, adapters.findMatching(adapter -> adapter == second).orElseThrow());
    }

    @Test
    void returnsEmptyForAnUnknownWeapon() {
        RecruitWeaponAdapters adapters = new RecruitWeaponAdapters(List.of(new TestAdapter()));

        assertTrue(adapters.findMatching(adapter -> false).isEmpty());
        assertFalse(adapters.isSupportedWeapon(null));
    }

    @Test
    void rejectsOverlappingAdapterClaims() {
        RecruitWeaponAdapters adapters = new RecruitWeaponAdapters(
                List.of(new TestAdapter(), new TestAdapter()));

        assertThrows(IllegalStateException.class, () -> adapters.findMatching(adapter -> true));
    }

    @Test
    void missingAmmoAndUnknownProjectileTypesAreUnsupported() {
        RecruitWeaponAdapters adapters = new RecruitWeaponAdapters(List.of(new TestAdapter()));

        assertFalse(adapters.isSupportedAmmo(null));
        assertFalse(adapters.isSupportedProjectile(null));
        assertFalse(adapters.isSupportedProjectile(String.class));
    }

    @Test
    void projectileTypesMustHaveOneUniqueOwningAdapter() {
        TestAdapter first = new TestAdapter(CharSequence.class);
        TestAdapter second = new TestAdapter(Number.class);
        RecruitWeaponAdapters adapters = new RecruitWeaponAdapters(List.of(first, second));

        assertSame(first, adapters.findProjectileType(String.class).orElseThrow());
        assertSame(second, adapters.findProjectileType(Integer.class).orElseThrow());

        RecruitWeaponAdapters overlapping = new RecruitWeaponAdapters(
                List.of(new TestAdapter(Object.class), second));
        assertThrows(IllegalStateException.class, () -> overlapping.findProjectileType(Number.class));
    }

    @Test
    void weaponSwapChangesAdapterAndClearsThePreviousTransientState() {
        TestAdapter stoneAdapter = new TestAdapter();
        TestAdapter stickAdapter = new TestAdapter();
        RecruitWeaponAdapters adapters = new RecruitWeaponAdapters(List.of(stoneAdapter, stickAdapter));

        RecruitWeaponAdapters.Selection first = adapters.transition(
                null,
                stoneAdapter,
                null);
        RecruitWeaponAdapters.Selection second = adapters.transition(
                first,
                stickAdapter,
                null);

        assertSame(stoneAdapter, first.adapter());
        assertSame(stickAdapter, second.adapter());
        assertEquals(1, stoneAdapter.clearCalls);
        assertSame(first.weapon(), stoneAdapter.lastClearedWeapon);
        assertEquals(0, stickAdapter.clearCalls);
    }

    private static final class TestAdapter implements BoomstickWeaponAdapter {
        private final Class<?> projectileBaseType;
        private int clearCalls;
        private ItemStack lastClearedWeapon;

        private TestAdapter() {
            this(null);
        }

        private TestAdapter(Class<?> projectileBaseType) {
            this.projectileBaseType = projectileBaseType;
        }

        @Override
        public boolean supports(ItemStack weapon) {
            return false;
        }

        @Override
        public boolean supportsAmmo(ItemStack ammo) {
            return false;
        }

        @Override
        public boolean supportsProjectile(Class<?> projectileType) {
            return projectileBaseType != null
                    && projectileType != null
                    && projectileBaseType.isAssignableFrom(projectileType);
        }

        @Override
        public Optional<BoomstickWeaponProfile> profile(ItemStack weapon) {
            return Optional.empty();
        }

        @Override
        public boolean isLoaded(ItemStack weapon) {
            return false;
        }

        @Override
        public void setLoaded(ItemStack weapon, boolean loaded) {
        }

        @Override
        public void setReloading(ItemStack weapon, boolean reloading) {
        }

        @Override
        public boolean isReloading(ItemStack weapon) {
            return false;
        }

        @Override
        public void setFiring(ItemStack weapon, boolean firing) {
        }

        @Override
        public void clearTransientState(ItemStack weapon) {
            clearCalls++;
            lastClearedWeapon = weapon;
        }

        @Override
        public int reloadTicks(ItemStack weapon) {
            return 0;
        }

        @Override
        public int cooldownTicks(ItemStack weapon) {
            return 0;
        }

        @Override
        public boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
            return false;
        }

        @Override
        public boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
            return false;
        }

        @Override
        public ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition) {
            return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
        }
    }
}
