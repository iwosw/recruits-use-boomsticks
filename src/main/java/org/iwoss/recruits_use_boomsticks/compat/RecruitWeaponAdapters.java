package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.world.item.ItemStack;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Ordered lookup for every ranged-weapon integration available to recruit AI. */
public final class RecruitWeaponAdapters {
    private static final RecruitWeaponAdapters PRODUCTION = new RecruitWeaponAdapters(
            List.of(MedievalBoomsticksAdapter.INSTANCE, ArtilleryAddonAdapter.INSTANCE));

    private final List<BoomstickWeaponAdapter> adapters;

    RecruitWeaponAdapters(List<BoomstickWeaponAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        this.adapters = List.copyOf(adapters);
        if (this.adapters.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("adapters must not contain null");
        }
    }

    public static RecruitWeaponAdapters production() {
        return PRODUCTION;
    }

    /** Returns the unique adapter claiming the stack and rejects ambiguous registrations. */
    public Optional<BoomstickWeaponAdapter> find(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return Optional.empty();
        }

        return findMatching(adapter -> adapter.supports(weapon));
    }

    /** Resolves only adapters whose integration is enabled by the global and local switches. */
    public Optional<BoomstickWeaponAdapter> findEnabled(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty() || !CompatConfig.ENABLED.get()) {
            return Optional.empty();
        }
        return findMatching(adapter -> CompatConfig.isIntegrationEnabled(adapter.integration())
                && adapter.supports(weapon));
    }

    Optional<BoomstickWeaponAdapter> findMatching(Predicate<BoomstickWeaponAdapter> claim) {
        Objects.requireNonNull(claim, "claim");
        BoomstickWeaponAdapter match = null;
        for (BoomstickWeaponAdapter adapter : adapters) {
            if (!claim.test(adapter)) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException("multiple recruit weapon adapters support the same stack");
            }
            match = adapter;
        }
        return Optional.ofNullable(match);
    }

    public boolean isSupportedWeapon(ItemStack weapon) {
        return find(weapon).isPresent();
    }

    public boolean isSupportedEnabledWeapon(ItemStack weapon) {
        return findEnabled(weapon).isPresent();
    }

    public Optional<BoomstickWeaponAdapter> findAmmo(ItemStack ammo) {
        if (ammo == null || ammo.isEmpty()) {
            return Optional.empty();
        }
        return findMatching(adapter -> adapter.supportsAmmo(ammo));
    }

    public boolean isSupportedAmmo(ItemStack ammo) {
        return findAmmo(ammo).isPresent();
    }

    public boolean isSupportedEnabledAmmo(ItemStack ammo) {
        if (ammo == null || ammo.isEmpty() || !CompatConfig.ENABLED.get()) {
            return false;
        }
        return findMatching(adapter -> CompatConfig.isIntegrationEnabled(adapter.integration())
                && adapter.supportsAmmo(ammo)).isPresent();
    }

    public Optional<BoomstickWeaponAdapter> findProjectileType(Class<?> projectileType) {
        if (projectileType == null) {
            return Optional.empty();
        }
        return findMatching(adapter -> adapter.supportsProjectile(projectileType));
    }

    public boolean isSupportedProjectile(Class<?> projectileType) {
        return findProjectileType(projectileType).isPresent();
    }

    public boolean isSupportedEnabledProjectile(Class<?> projectileType) {
        if (projectileType == null || !CompatConfig.ENABLED.get()) {
            return false;
        }
        return findMatching(adapter -> CompatConfig.isIntegrationEnabled(adapter.integration())
                && adapter.supportsProjectile(projectileType)).isPresent();
    }

    public boolean hasEnabledAdapter() {
        if (!CompatConfig.ENABLED.get()) {
            return false;
        }
        return adapters.stream().anyMatch(adapter -> CompatConfig.isIntegrationEnabled(adapter.integration()));
    }

    /**
     * Resolves an active weapon and cleans animation state through the adapter that owned the
     * previous stack before allowing a different adapter to take over.
     */
    public Optional<Selection> select(ItemStack weapon, Selection previous) {
        if (previous != null && previous.weapon() == weapon && previous.adapter().supports(weapon)) {
            return Optional.of(previous);
        }
        Optional<BoomstickWeaponAdapter> nextAdapter = find(weapon);
        if (nextAdapter.isEmpty()) {
            clear(previous);
            return Optional.empty();
        }
        return Optional.of(transition(previous, nextAdapter.orElseThrow(), weapon));
    }

    public Optional<Selection> selectEnabled(ItemStack weapon, Selection previous) {
        if (!CompatConfig.ENABLED.get()) {
            return Optional.empty();
        }
        if (previous != null
                && previous.weapon() == weapon
                && CompatConfig.isIntegrationEnabled(previous.adapter().integration())
                && previous.adapter().supports(weapon)) {
            return Optional.of(previous);
        }
        Optional<BoomstickWeaponAdapter> nextAdapter = findEnabled(weapon);
        if (nextAdapter.isEmpty()) {
            clear(previous);
            return Optional.empty();
        }
        return Optional.of(transition(previous, nextAdapter.orElseThrow(), weapon));
    }

    Selection transition(Selection previous, BoomstickWeaponAdapter nextAdapter, ItemStack weapon) {
        Objects.requireNonNull(nextAdapter, "nextAdapter");
        if (previous != null && previous.adapter() == nextAdapter && previous.weapon() == weapon) {
            return previous;
        }
        clear(previous);
        return new Selection(nextAdapter, weapon);
    }

    private static void clear(Selection previous) {
        if (previous != null) {
            previous.adapter().clearTransientState(previous.weapon());
        }
    }

    public record Selection(BoomstickWeaponAdapter adapter, ItemStack weapon) {
        public Selection {
            Objects.requireNonNull(adapter, "adapter");
        }
    }
}
