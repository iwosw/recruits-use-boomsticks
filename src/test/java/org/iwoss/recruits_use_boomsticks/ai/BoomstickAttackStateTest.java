package org.iwoss.recruits_use_boomsticks.ai;

import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter.ShotOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoomstickAttackStateTest {
    @Test
    void reloadsEquippedWeaponBeforeAnyTargetIsAssigned() {
        BoomstickAttackState state = new BoomstickAttackState();

        assertEquals(BoomstickAttackState.Phase.RELOAD, state.advance(
                signals(true, false, true, false, true, false, false, false, null)));
        assertEquals(BoomstickAttackState.Phase.IDLE, state.advance(
                signals(true, false, true, false, true, true, false, false, null)));
    }

    @Test
    void acquiresReloadsAimsAndFiresInOrder() {
        BoomstickAttackState state = new BoomstickAttackState();

        assertEquals(BoomstickAttackState.Phase.ACQUIRE_WEAPON, state.advance(
                signals(true, true, false, false, true, false, false, false, null)));
        assertEquals(BoomstickAttackState.Phase.RELOAD, state.advance(
                signals(true, true, true, false, true, false, false, false, null)));
        assertEquals(BoomstickAttackState.Phase.AIM, state.advance(
                signals(true, true, true, true, true, true, false, false, null)));
        assertEquals(BoomstickAttackState.Phase.FIRE, state.advance(
                signals(true, true, true, true, true, false, true, false, null)));
        assertEquals(BoomstickAttackState.Phase.COOLDOWN, state.advance(
                signals(true, true, true, false, true, false, false, false, ShotOutcome.FIRED)));
    }

    @Test
    void insufficientAmmoDoesNotStartReloadAndRecoversLater() {
        BoomstickAttackState state = new BoomstickAttackState();

        assertEquals(BoomstickAttackState.Phase.OUT_OF_AMMO, state.advance(
                signals(true, true, true, false, false, false, false, false, null)));
        assertEquals(BoomstickAttackState.Phase.RELOAD, state.advance(
                signals(true, true, true, false, true, false, false, false, null)));
    }

    @Test
    void targetLossDuringAimReturnsToIdle() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, true, true, false, false, false, null));

        assertEquals(BoomstickAttackState.Phase.IDLE, state.advance(
                signals(true, false, true, true, true, false, false, false, null)));
    }

    @Test
    void reloadInterruptionAndDisableAlwaysCleanUpToIdle() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, false, true, false, false, false, null));

        assertEquals(BoomstickAttackState.Phase.IDLE, state.advance(
                signals(true, true, false, false, true, false, false, false, null)));
        state.advance(signals(true, true, true, false, true, false, false, false, null));
        assertEquals(BoomstickAttackState.Phase.IDLE, state.advance(
                signals(false, true, true, false, true, false, false, false, null)));
    }

    @Test
    void strategicFireUsesTheSameTargetSignal() {
        BoomstickAttackState state = new BoomstickAttackState();

        assertEquals(BoomstickAttackState.Phase.ACQUIRE_WEAPON, state.advance(
                signals(true, true, false, false, true, false, false, false, null)));
    }

    @Test
    void failedShotMovesToOutOfAmmoAndRecoversWhenAmmoReturns() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, true, true, false, false, false, null));
        state.advance(signals(true, true, true, true, true, false, true, false, null));

        assertEquals(BoomstickAttackState.Phase.OUT_OF_AMMO, state.advance(
                signals(true, true, true, false, true, false, false, false, ShotOutcome.NO_AMMO)));
        assertEquals(BoomstickAttackState.Phase.RELOAD, state.advance(
                signals(true, true, true, false, true, false, false, false, null)));
    }

    @Test
    void completedCooldownReturnsToAimForAStillLoadedWeapon() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, true, true, false, false, false, null));
        state.advance(signals(true, true, true, true, true, false, true, false, null));
        state.advance(signals(true, true, true, false, true, false, false, false, ShotOutcome.FIRED));

        assertEquals(BoomstickAttackState.Phase.AIM, state.advance(
                signals(true, true, true, true, true, false, false, true, null)));
    }

    @Test
    void targetLossDoesNotCancelAnActiveCooldown() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, true, true, false, false, false, null));
        state.advance(signals(true, true, true, true, true, false, true, false, null));
        state.advance(signals(true, true, true, false, true, false, false, false, ShotOutcome.FIRED));

        assertEquals(BoomstickAttackState.Phase.COOLDOWN, state.advance(
                signals(true, false, true, false, true, false, false, false, null)));
        assertEquals(BoomstickAttackState.Phase.IDLE, state.advance(
                signals(true, false, true, false, true, false, false, true, null)));
    }

    @Test
    void targetLossImmediatelyAfterACommittedShotStillStartsCooldown() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, true, true, false, false, false, null));
        state.advance(signals(true, true, true, true, true, false, true, false, null));

        assertEquals(BoomstickAttackState.Phase.COOLDOWN, state.advance(
                signals(true, false, true, false, true, false, false, false, ShotOutcome.FIRED)));
    }

    @Test
    void weaponSwapDuringReloadAbortsWithoutEnteringAim() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, false, true, false, false, false, null));

        assertEquals(BoomstickAttackState.Phase.IDLE, state.advance(
                signals(true, true, false, false, true, true, false, false, null)));
    }

    @Test
    void inactiveSignalsResetAnyActivePhase() {
        BoomstickAttackState state = new BoomstickAttackState();
        state.advance(signals(true, true, true, true, true, false, false, false, null));

        assertEquals(BoomstickAttackState.Phase.IDLE, state.advance(
                signals(false, true, true, true, true, true, true, true, ShotOutcome.FIRED)));
    }

    private static BoomstickAttackState.Signals signals(
            boolean active,
            boolean targetAvailable,
            boolean weaponInMainHand,
            boolean loaded,
            boolean ammoAvailable,
            boolean reloadComplete,
            boolean aimComplete,
            boolean cooldownComplete,
            ShotOutcome shotOutcome
    ) {
        return new BoomstickAttackState.Signals(
                active,
                targetAvailable,
                weaponInMainHand,
                loaded,
                ammoAvailable,
                reloadComplete,
                aimComplete,
                cooldownComplete,
                shotOutcome
        );
    }
}
