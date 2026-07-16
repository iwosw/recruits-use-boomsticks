package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.world.entity.projectile.Arrow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtilleryAdapterContractTest {
    @Test
    void keepsOptionalModAvailabilitySeparateFromRegistryIdentity() {
        ArtilleryAddonAdapter unavailable = new ArtilleryAddonAdapter(() -> false);
        ArtilleryAddonAdapter available = new ArtilleryAddonAdapter(() -> true);

        assertFalse(unavailable.isAvailable());
        assertTrue(available.isAvailable());
        assertFalse(available.supportsProjectile(Arrow.class));
        assertTrue(available.supportsProjectileClassName(
                "net.mcreator.artilleryaddon.entity.IronballProjectileEntity"));
        assertFalse(available.supportsProjectileClassName(
                "net.mcreator.artilleryaddon.entity.IronBitProjectileEntity"));
        assertFalse(available.supportsProjectileClassName(Arrow.class.getName()));
        assertFalse(available.supportsProjectileClassName(String.class.getName()));
    }

    @Test
    void reloadResultReportsACommittedTransaction() {
        BoomstickWeaponAdapter.ReloadResult result = BoomstickWeaponAdapter.ReloadResult.reloaded(1);

        assertTrue(result.reloaded());
        assertTrue(result.committed());
        assertFalse(result.needsAmmo());
    }
}
