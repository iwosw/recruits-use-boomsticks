package org.iwoss.recruits_use_boomsticks.ai;

import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter.ShotOutcome;

import java.util.Objects;

/**
 * Pure, deterministic combat lifecycle for a boomstick-equipped crossbowman.
 *
 * <p>The state machine does not inspect Minecraft entities or mutate inventory. The goal supplies
 * a snapshot of the world and performs side effects only after the resulting phase is known.</p>
 */
public final class BoomstickAttackState {
    public enum Phase {
        IDLE,
        ACQUIRE_WEAPON,
        RELOAD,
        AIM,
        FIRE,
        COOLDOWN,
        OUT_OF_AMMO
    }

    /** Immutable world snapshot consumed by one state-machine tick. */
    public record Signals(
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
    }

    private Phase phase = Phase.IDLE;

    public Phase phase() {
        return phase;
    }

    public Phase advance(Signals signals) {
        Objects.requireNonNull(signals, "signals");
        phase = nextPhase(phase, signals);
        return phase;
    }

    public void reset() {
        phase = Phase.IDLE;
    }

    private static Phase nextPhase(Phase current, Signals signals) {
        if (!signals.active()) {
            return Phase.IDLE;
        }

        return switch (current) {
            case IDLE -> fromIdle(signals);
            case ACQUIRE_WEAPON -> fromAcquire(signals);
            case RELOAD -> fromReload(signals);
            case AIM -> fromAim(signals);
            case FIRE -> fromFire(signals);
            case COOLDOWN -> fromCooldown(signals);
            case OUT_OF_AMMO -> fromOutOfAmmo(signals);
        };
    }

    private static Phase fromIdle(Signals signals) {
        if (!signals.weaponInMainHand()) {
            return signals.targetAvailable() ? Phase.ACQUIRE_WEAPON : Phase.IDLE;
        }
        if (!signals.loaded()) {
            if (signals.ammoAvailable()) {
                return Phase.RELOAD;
            }
            return signals.targetAvailable() ? Phase.OUT_OF_AMMO : Phase.IDLE;
        }
        return signals.targetAvailable() ? Phase.AIM : Phase.IDLE;
    }

    private static Phase fromAcquire(Signals signals) {
        if (!signals.weaponInMainHand()) {
            return signals.targetAvailable() ? Phase.ACQUIRE_WEAPON : Phase.IDLE;
        }
        if (!signals.loaded()) {
            if (signals.ammoAvailable()) {
                return Phase.RELOAD;
            }
            return signals.targetAvailable() ? Phase.OUT_OF_AMMO : Phase.IDLE;
        }
        return signals.targetAvailable() ? Phase.AIM : Phase.IDLE;
    }

    private static Phase fromReload(Signals signals) {
        if (!signals.weaponInMainHand()) {
            return Phase.IDLE;
        }
        if (!signals.ammoAvailable() && !signals.loaded()) {
            return Phase.OUT_OF_AMMO;
        }
        if (!signals.reloadComplete()) {
            return Phase.RELOAD;
        }
        return signals.targetAvailable() ? Phase.AIM : Phase.IDLE;
    }

    private static Phase fromAim(Signals signals) {
        if (!signals.weaponInMainHand() || !signals.targetAvailable()) {
            return Phase.IDLE;
        }
        if (!signals.loaded()) {
            return signals.ammoAvailable() ? Phase.RELOAD : Phase.OUT_OF_AMMO;
        }
        return signals.aimComplete() ? Phase.FIRE : Phase.AIM;
    }

    private static Phase fromFire(Signals signals) {
        if (!signals.weaponInMainHand()) {
            return Phase.IDLE;
        }
        if (signals.shotOutcome() != null) {
            return switch (signals.shotOutcome()) {
                case FIRED -> Phase.COOLDOWN;
                case NO_AMMO -> Phase.OUT_OF_AMMO;
                case INVALID_WEAPON, INVALID_TARGET, CLIENT_SIDE_REJECTED, NOT_LOADED, SPAWN_FAILED -> Phase.IDLE;
            };
        }
        return signals.targetAvailable() ? Phase.FIRE : Phase.IDLE;
    }

    private static Phase fromCooldown(Signals signals) {
        if (!signals.weaponInMainHand()) {
            return Phase.IDLE;
        }
        if (!signals.cooldownComplete()) {
            return Phase.COOLDOWN;
        }
        if (!signals.targetAvailable()) {
            return Phase.IDLE;
        }
        if (signals.loaded()) {
            return Phase.AIM;
        }
        return signals.ammoAvailable() ? Phase.RELOAD : Phase.OUT_OF_AMMO;
    }

    private static Phase fromOutOfAmmo(Signals signals) {
        if (!signals.weaponInMainHand() || !signals.targetAvailable()) {
            return Phase.IDLE;
        }
        if (signals.loaded()) {
            return Phase.AIM;
        }
        return signals.ammoAvailable() ? Phase.RELOAD : Phase.OUT_OF_AMMO;
    }
}
