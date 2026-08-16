package org.iwoss.recruits_use_boomsticks.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadStep.ComponentRequirement;
import org.iwoss.recruits_use_boomsticks.compat.ArtilleryReloadStep.ComponentUse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the bytecode-confirmed native loading chains.
 *
 * <p>The expectations mirror the loading branches of the addon's own right-click procedures, read
 * from the mapped 1.14.0 artifact and re-read from the pinned server-safe 1.11 artifact.</p>
 */
class ArtilleryReloadProtocolTest {

    @Test
    void everyEnabledGameplayWeaponWalksANativeChain() {
        for (String weaponId : SupportedArtillery.gameplayWeaponIds()) {
            assertTrue(ArtilleryReloadProtocol.hasSteppedChain(weaponId),
                    "the enabled gameplay weapon " + weaponId + " must load through its native chain");
        }
    }

    @Test
    void everyChainEndsAtTheProfilesConfirmedLoadedState() {
        for (String weaponId : SupportedArtillery.gameplayWeaponIds()) {
            ArtilleryWeaponProfile profile = SupportedArtillery.profileFor(weaponId).orElseThrow();
            CompoundTag weapon = new CompoundTag();

            // A repeater is fireable from its first committed round, exactly like the native counter;
            // every other protocol must stay unloaded until its chain finishes.
            boolean repeater = profile.nativeStateMode()
                    == ArtilleryWeaponProfile.NativeStateMode.AMMO_COUNT;
            List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(weaponId);
            for (int index = 0; index < steps.size(); index++) {
                ArtilleryNativeState.applyReloadStepTag(weapon, steps.get(index));
                boolean lastStep = index == steps.size() - 1;
                if (!lastStep && !repeater) {
                    assertFalse(ArtilleryNativeState.isLoadedTag(weapon, profile),
                            weaponId + " must not report itself loaded before its chain finishes");
                }
            }

            assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile),
                    "the finished native chain must leave " + weaponId + " loaded");
        }
    }

    @Test
    void partialNativeStateIdentifiesTheLastCompletedReloadStep() {
        for (String weaponId : SupportedArtillery.gameplayWeaponIds()) {
            ArtilleryWeaponProfile profile = SupportedArtillery.profileFor(weaponId).orElseThrow();
            List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(profile);
            CompoundTag weapon = new CompoundTag();

            assertEquals(0, ArtilleryNativeState.completedReloadStepsTag(weapon, steps));
            for (int index = 0; index < steps.size(); index++) {
                ArtilleryNativeState.applyReloadStepTag(weapon, steps.get(index));
                assertEquals(index + 1, ArtilleryNativeState.completedReloadStepsTag(weapon, steps),
                        weaponId + " must resume after its last committed native step");
            }
        }
    }

    @Test
    void firedCarbineDoesNotLookLikeACompletedReload() {
        ArtilleryWeaponProfile profile = SupportedArtillery
                .profileFor(SupportedArtillery.MATCHLOCK_CARBINE_ID)
                .orElseThrow();
        List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(profile);
        CompoundTag weapon = new CompoundTag();

        ArtilleryNativeState.markFiredTag(weapon, profile);

        assertEquals(0, ArtilleryNativeState.completedReloadStepsTag(weapon, steps));
    }

    @Test
    void everyChainSpendsExactlyTheNativeAmmunition() {
        for (String weaponId : SupportedArtillery.gameplayWeaponIds()) {
            ArtilleryWeaponProfile profile = SupportedArtillery.profileFor(weaponId).orElseThrow();
            int consumed = 0;
            for (ArtilleryReloadStep step : ArtilleryReloadProtocol.stepsFor(weaponId)) {
                if (step.componentUse() != ComponentUse.CONSUME_ONE) {
                    continue;
                }
                consumed++;
                assertEquals(1, step.components().size(),
                        weaponId + " must consume one exact ammunition identity per step");
                assertEquals(profile.ammoId(), step.components().get(0).id(),
                        weaponId + " must consume the ammunition its profile declares");
            }
            assertEquals(ArtilleryReloadProtocol.ammoConsumed(weaponId), consumed,
                    weaponId + " must report the ammunition its own chain spends");
            assertEquals(profile.magazineSize(), consumed,
                    weaponId + " loads one round per magazine slot, however many projectiles it fires");
        }
    }

    @Test
    void aMultiProjectileVolleyStillLoadsOneNativeBall() {
        // HandcannonRightclickProcedure shrinks one IRON_BALL and later spawns its whole volley.
        assertEquals(1, ArtilleryReloadProtocol.ammoConsumed(SupportedArtillery.HAND_CANNON_ID));
        assertEquals(
                ArtilleryReloadProtocol.CHU_KO_NU_CAPACITY,
                ArtilleryReloadProtocol.ammoConsumed(SupportedArtillery.CHU_KO_NU_ID));
    }

    @Test
    void onlyToolsAreDamagedAndBareHandStepsCostNothing() {
        for (String weaponId : SupportedArtillery.gameplayWeaponIds()) {
            for (ArtilleryReloadStep step : ArtilleryReloadProtocol.stepsFor(weaponId)) {
                if (step.componentUse() == ComponentUse.NONE) {
                    assertFalse(step.requiresCarriedComponent(),
                            weaponId + " must only skip the cost of a native bare-hand step");
                }
                if (step.componentUse() == ComponentUse.CONSUME_ONE) {
                    assertTrue(step.requiresCarriedComponent(),
                            weaponId + " must never consume an empty hand");
                }
            }
        }
    }

    @Test
    void theMatchlockFamilyLoadsPowderThenBallThenRamrod() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.ARQUEBUS_ID);

        assertEquals(3, steps.size());
        assertEquals(
                List.of(ComponentRequirement.tag(ArtilleryReloadProtocol.POWDER_FLASK_TAG)),
                steps.get(0).components());
        assertEquals(ComponentUse.DAMAGE_ONE, steps.get(0).componentUse());
        assertEquals(BoomstickSound.ARTILLERY_LOADING_POWDER, steps.get(0).sound());
        assertEquals(0.0D, stageAfter(steps.get(0)));
        assertEquals(1.0D, numberWrite(steps.get(0), ArtilleryNativeState.POWDER_KEY));

        assertEquals(
                List.of(ComponentRequirement.item(SupportedArtillery.IRON_BALL_ID)),
                steps.get(1).components());
        assertEquals(ComponentUse.CONSUME_ONE, steps.get(1).componentUse());
        assertEquals(1.0D, stageAfter(steps.get(1)));

        assertEquals(
                List.of(ComponentRequirement.tag(ArtilleryReloadProtocol.RAMROD_TAG)),
                steps.get(2).components());
        assertEquals(ComponentUse.DAMAGE_ONE, steps.get(2).componentUse());
        assertEquals(BoomstickSound.ARTILLERY_RAMMING, steps.get(2).sound());
        assertEquals(2.0D, stageAfter(steps.get(2)));
    }

    @Test
    void theMusketUsesItsOwnNativeBallSound() {
        assertEquals(
                BoomstickSound.ARTILLERY_HAND_CANNON_LOAD_BALL,
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.MATCHLOCK_MUSKET_ID).get(1).sound());
        assertEquals(
                BoomstickSound.ARTILLERY_LOAD_BALL,
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.TORADAR_RIFLE_ID).get(1).sound());
    }

    @Test
    void hackbutLoadsTheLargeBallThroughTheSameMatchlockChain() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.HACKBUT_ID);

        assertEquals(3, steps.size());
        assertEquals(
                List.of(ComponentRequirement.item(SupportedArtillery.LARGE_IRON_BALL_ID)),
                steps.get(1).components());
    }

    @Test
    void carbineAndHarquebusShiftEveryNativeStageByOneAndRamBareHanded() {
        for (String weaponId : List.of(
                SupportedArtillery.MATCHLOCK_CARBINE_ID,
                SupportedArtillery.HARQUEBUS_ID)) {
            List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(weaponId);

            assertEquals(3, steps.size(), weaponId + " keeps the three native loading steps");
            assertEquals(1.0D, stageAfter(steps.get(0)));
            assertEquals(2.0D, stageAfter(steps.get(1)));
            assertEquals(3.0D, stageAfter(steps.get(2)));
            assertEquals(List.of(ComponentRequirement.EMPTY_HAND), steps.get(2).components(),
                    weaponId + " rams with the native bare hand and takes no ramrod");
            assertFalse(steps.get(2).requiresCarriedComponent());
        }
    }

    @Test
    void theHandCannonFamilyAcceptsABareHandBeforeTheRamrod() {
        for (String weaponId : List.of(
                SupportedArtillery.HANDGONNE_ID,
                SupportedArtillery.TACCOLA_HANDGONNE_ID,
                SupportedArtillery.HAND_CANNON_ID,
                SupportedArtillery.MATCHLOCK_PISTOL_ID)) {
            List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(weaponId);
            ArtilleryReloadStep ramming = steps.get(steps.size() - 1);

            assertEquals(
                    List.of(
                            ComponentRequirement.EMPTY_HAND,
                            ComponentRequirement.tag(ArtilleryReloadProtocol.RAMROD_TAG)),
                    ramming.components(),
                    weaponId + " checks the bare hand before the ramrod, exactly like the native branch");
        }
    }

    @Test
    void handgonneAndTaccolaWriteTheNativeBallAndLoadedMarkers() {
        for (String weaponId : List.of(
                SupportedArtillery.HANDGONNE_ID,
                SupportedArtillery.TACCOLA_HANDGONNE_ID,
                SupportedArtillery.BRONZE_HANDGONNE_ID)) {
            List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(weaponId);

            assertEquals(0.0D, numberWrite(steps.get(1), ArtilleryNativeState.AMMO_KEY));
            assertEquals(BoomstickSound.ARTILLERY_HAND_CANNON_RAMMING, steps.get(2).sound());
            assertTrue(steps.get(2).write(ArtilleryNativeState.LOADED_KEY).orElseThrow().flag(),
                    weaponId + " commits the native loaded flag with its ramming step");
        }

        // The plain Hand Cannon writes neither marker; its native firing branch only checks stage 2.0.
        List<ArtilleryReloadStep> handCannon =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.HAND_CANNON_ID);
        assertTrue(handCannon.get(1).write(ArtilleryNativeState.AMMO_KEY).isEmpty());
        assertTrue(handCannon.get(2).write(ArtilleryNativeState.LOADED_KEY).isEmpty());
    }

    @Test
    void tillerGunRamsWithABareHandOrItsPowderFlask() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.TILLER_GUN_ID);

        assertEquals(
                List.of(
                        ComponentRequirement.EMPTY_HAND,
                        ComponentRequirement.tag(ArtilleryReloadProtocol.POWDER_FLASK_TAG)),
                steps.get(2).components());
        assertTrue(steps.get(2).write(ArtilleryNativeState.LOADED_KEY).orElseThrow().flag());
    }

    @Test
    void miniPistolaReachesItsFiringStageWithTheBallStep() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.MINI_PISTOLA_ID);

        assertEquals(2, steps.size(), "the native Mini Pistola branch has no ramming step");
        assertEquals(
                List.of(ComponentRequirement.item(SupportedArtillery.SMALL_IRON_BALL_ID)),
                steps.get(1).components());
        assertEquals(1.0D, stageAfter(steps.get(1)));
    }

    @Test
    void theArrowGonnesJumpStraightToTheirNativeArrowStage() {
        for (String weaponId : List.of(
                SupportedArtillery.NOBLE_HANDGONNE_ID,
                SupportedArtillery.MARKMENGONNE_ID)) {
            List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(weaponId);

            assertEquals(2, steps.size(), weaponId + " loads powder and one arrow, and never rams");
            assertEquals(
                    List.of(ComponentRequirement.item(SupportedArtillery.VANILLA_ARROW_ID)),
                    steps.get(1).components());
            assertEquals(2.0D, stageAfter(steps.get(1)));
            assertEquals(2.0D, numberWrite(steps.get(1), ArtilleryNativeState.AMMO_KEY));
            assertTrue(steps.get(1).write(ArtilleryNativeState.LOADED_KEY).isEmpty(),
                    weaponId + " must not invent a native loaded flag for its Arrow branch");
        }
    }

    @Test
    void nobleHandgonneIronBallBranchLoadsAndRamsSeparatelyFromItsArrowBranch() {
        ArtilleryWeaponProfile profile = SupportedArtillery.nobleHandgonneIronBallProfile();
        List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(profile);

        assertEquals(3, steps.size());
        assertEquals(
                List.of(ComponentRequirement.item(SupportedArtillery.IRON_BALL_ID)),
                steps.get(1).components());
        assertEquals(1.0D, stageAfter(steps.get(1)));
        assertEquals(0.0D, numberWrite(steps.get(1), ArtilleryNativeState.AMMO_KEY));
        assertEquals(
                List.of(
                        ComponentRequirement.EMPTY_HAND,
                        ComponentRequirement.tag(ArtilleryReloadProtocol.RAMROD_TAG)),
                steps.get(2).components());
        assertEquals(2.0D, stageAfter(steps.get(2)));
        assertTrue(steps.get(2).write(ArtilleryNativeState.LOADED_KEY).orElseThrow().flag());

        CompoundTag weapon = new CompoundTag();
        steps.forEach(step -> ArtilleryNativeState.applyReloadStepTag(weapon, step));
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertEquals(1, ArtilleryReloadProtocol.ammoConsumed(profile));
    }

    @Test
    void markmengonneIronBallBranchLoadsAndRamsWithoutInventingLoadedFlag() {
        ArtilleryWeaponProfile profile = SupportedArtillery.markmengonneIronBallProfile();
        List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(profile);

        assertEquals(3, steps.size());
        assertEquals(
                List.of(ComponentRequirement.item(SupportedArtillery.IRON_BALL_ID)),
                steps.get(1).components());
        assertEquals(0.0D, numberWrite(steps.get(1), ArtilleryNativeState.AMMO_KEY));
        assertEquals(
                List.of(
                        ComponentRequirement.item(SupportedArtillery.VANILLA_STICK_ID),
                        ComponentRequirement.tag(ArtilleryReloadProtocol.RAMROD_TAG)),
                steps.get(2).components());
        assertTrue(steps.get(2).write(ArtilleryNativeState.LOADED_KEY).isEmpty());

        CompoundTag weapon = new CompoundTag();
        steps.forEach(step -> ArtilleryNativeState.applyReloadStepTag(weapon, step));
        assertTrue(ArtilleryNativeState.isLoadedTag(weapon, profile));
        assertFalse(weapon.contains(ArtilleryNativeState.LOADED_KEY));
        assertEquals(1, ArtilleryReloadProtocol.ammoConsumed(profile));
    }

    @Test
    void bronzeHandgonneRamsWithAStickOrRamrod() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.BRONZE_HANDGONNE_ID);

        assertEquals(
                List.of(
                        ComponentRequirement.item(SupportedArtillery.VANILLA_STICK_ID),
                        ComponentRequirement.tag(ArtilleryReloadProtocol.RAMROD_TAG)),
                steps.get(2).components());
        assertTrue(steps.get(2).write(ArtilleryNativeState.LOADED_KEY).orElseThrow().flag());
    }

    @Test
    void doubleBarrelGonneWalksItsFirstBarrelMarkersOnly() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.DOUBLE_BARREL_GONNE_ID);

        assertEquals(3, steps.size());
        assertEquals(1.0D, numberWrite(steps.get(0), ArtilleryNativeState.BARREL_ONE_KEY));
        assertEquals(0.0D, numberWrite(steps.get(0), ArtilleryNativeState.RAMMED_ONE_KEY));
        assertEquals(2.0D, numberWrite(steps.get(1), ArtilleryNativeState.BARREL_ONE_KEY));
        assertEquals(1.0D, numberWrite(steps.get(2), ArtilleryNativeState.RAMMED_ONE_KEY));
        assertEquals(1.0D, numberWrite(steps.get(2), ArtilleryNativeState.LOADED_KEY));

        CompoundTag weapon = new CompoundTag();
        steps.forEach(step -> ArtilleryNativeState.applyReloadStepTag(weapon, step));
        assertFalse(weapon.contains(ArtilleryNativeState.STAGE_KEY),
                "the double-barrel protocol has no staged marker at all");
        assertFalse(weapon.contains(ArtilleryNativeState.POWDER_KEY));
        assertFalse(weapon.contains(ArtilleryNativeState.BARREL_TWO_KEY),
                "the second barrel stays outside the enabled first-barrel slice");
        assertEquals(Tag.TAG_DOUBLE, weapon.getTagType(ArtilleryNativeState.LOADED_KEY));
    }

    @Test
    void windlassCrossbowCocksThreeTimesBeforeItTakesAnArrow() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.WINDLASS_CROSSBOW_ID);

        assertEquals(4, steps.size());
        assertEquals(BoomstickSound.CROSSBOW_LOADING_START, steps.get(0).sound());
        assertEquals(BoomstickSound.CROSSBOW_LOADING_MIDDLE, steps.get(1).sound());
        assertEquals(BoomstickSound.CROSSBOW_LOADING_MIDDLE, steps.get(2).sound());
        assertEquals(BoomstickSound.CROSSBOW_LOADING_END, steps.get(3).sound());
        for (int index = 0; index < 3; index++) {
            assertEquals(ComponentUse.NONE, steps.get(index).componentUse());
            assertEquals((double) (index + 1), stageAfter(steps.get(index)));
        }
        assertEquals(
                List.of(ComponentRequirement.item(SupportedArtillery.VANILLA_ARROW_ID)),
                steps.get(3).components());
        assertEquals(4.0D, stageAfter(steps.get(3)));
    }

    @Test
    void chuKoNuFillsItsNativeEightRoundMagazineOneArrowAtATime() {
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(SupportedArtillery.CHU_KO_NU_ID);

        assertEquals(ArtilleryReloadProtocol.CHU_KO_NU_CAPACITY, steps.size());
        for (int index = 0; index < steps.size(); index++) {
            ArtilleryReloadStep step = steps.get(index);
            assertEquals(ComponentUse.CONSUME_ONE, step.componentUse());
            assertEquals(
                    List.of(ComponentRequirement.item(SupportedArtillery.VANILLA_ARROW_ID)),
                    step.components());
            assertEquals((double) (index + 1), numberWrite(step, ArtilleryNativeState.AMMO_KEY));
            assertEquals("§7Ammo " + (index + 1) + "/8", step.nativeLore());
            assertTrue(step.write(ArtilleryNativeState.STAGE_KEY).isEmpty(),
                    "the native repeater tracks no stage marker");
        }
    }

    private static double stageAfter(ArtilleryReloadStep step) {
        return numberWrite(step, ArtilleryNativeState.STAGE_KEY);
    }

    private static double numberWrite(ArtilleryReloadStep step, String key) {
        return step.write(key).orElseThrow().number();
    }
}
