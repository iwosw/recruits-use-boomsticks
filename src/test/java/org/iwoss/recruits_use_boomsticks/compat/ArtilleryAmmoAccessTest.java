package org.iwoss.recruits_use_boomsticks.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArtilleryAmmoAccessTest {
    @Test
    void countsAndConsumesOnlyTheExactNativeAmmoIdentity() {
        List<ArtilleryAmmoAccess.AmmoSlot> inventory = List.of(
                new ArtilleryAmmoAccess.AmmoSlot(SupportedArtillery.VANILLA_ARROW_ID, 2),
                new ArtilleryAmmoAccess.AmmoSlot("minecraft:stick", 4)
        );

        assertEquals(2, ArtilleryAmmoAccess.countSlots(inventory, SupportedArtillery.VANILLA_ARROW_ID));
        assertEquals(0, ArtilleryAmmoAccess.countSlots(inventory, SupportedArtillery.IRON_BALL_ID));
        assertArrayEquals(
                new int[]{1, 0},
                ArtilleryAmmoAccess.planConsumption(
                        inventory,
                        SupportedArtillery.VANILLA_ARROW_ID,
                        1
                ).orElseThrow()
        );
        assertFalse(ArtilleryAmmoAccess.planConsumption(
                inventory,
                SupportedArtillery.VANILLA_ARROW_ID,
                3
        ).isPresent());
    }

    @Test
    void keepsMiniPistolaSmallBallsSeparateFromStandardIronBalls() {
        List<ArtilleryAmmoAccess.AmmoSlot> inventory = List.of(
                new ArtilleryAmmoAccess.AmmoSlot("artillery_addon:small_iron_ball", 2),
                new ArtilleryAmmoAccess.AmmoSlot(SupportedArtillery.IRON_BALL_ID, 1)
        );

        assertEquals(2, ArtilleryAmmoAccess.countSlots(inventory, "artillery_addon:small_iron_ball"));
        assertEquals(1, ArtilleryAmmoAccess.countSlots(inventory, SupportedArtillery.IRON_BALL_ID));
        assertFalse(ArtilleryAmmoAccess.planConsumption(
                inventory,
                "artillery_addon:small_iron_ball",
                3
        ).isPresent());
    }
}
