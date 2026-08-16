package org.iwoss.recruits_use_boomsticks.ai;

import com.mojang.logging.LogUtils;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickAmmoAccess;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.iwoss.recruits_use_boomsticks.inventory.RecruitHandSwap;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Shared ranged goal for adapter-backed weapons on Recruits crossbowmen. */
public final class RecruitBoomstickAttackGoal extends Goal {
    private enum Mode {
        COMBAT,
        PASSIVE_RELOAD
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FIRE_ANIMATION_TICKS = 3;
    /**
     * Backoff after a shot the current setup can never complete.
     *
     * <p>Some weapons reject the shot itself rather than the target — a Matchlock Carbine whose
     * native branch gate needs an empty or fork-rest off hand is the standing example. Without a
     * backoff the goal would re-enter the aim window and retry the same doomed shot forever, so the
     * recruit spins on a full aim cycle every few ticks and never reports why.</p>
     */
    private static final int REJECTED_SHOT_BACKOFF_TICKS = 40;
    private static final int AIM_RESERVATION_LEASE_TICKS = 5;
    /**
     * Pacing and reach of the search for an enemy the formation has not covered.
     *
     * <p>The interval and stagger keep a whole firing line from sweeping the area in the same tick.
     * The range deliberately stops short of {@link #MAX_COMBAT_RANGE}: a long musket may aim further
     * than this, but a forty-five block search box is a far more expensive lookup than the extra
     * reach is worth for a recruit that is only choosing between enemies.</p>
     */
    private static final int TARGET_SCAN_INTERVAL_TICKS = 20;
    private static final int TARGET_SCAN_STAGGER_TICKS = 7;
    private static final int MAX_TARGET_SCAN_LINE_OF_SIGHT_CHECKS = 4;
    private static final double MAX_TARGET_SCAN_RANGE = 32.0D;
    private static final int MIN_PROJECTILE_RESERVATION_TICKS = 20;
    private static final int MAX_PROJECTILE_RESERVATION_TICKS = 100;
    private static final double MAX_COMBAT_RANGE = 45.0D;
    private static final double HOLD_POSITION_RADIUS = 4.0D;
    private static final double EMERGENCY_TNT_RADIUS = 10.0D;
    private static final String COOLDOWN_UNTIL_TAG = "recruits_use_boomsticks:boomstick_cooldown_until";

    private final CrossBowmanEntity crossBowman;
    private final double speedModifier;
    private final RecruitWeaponAdapters adapters;
    private final Mode mode;
    private final BoomstickAttackState state = new BoomstickAttackState();
    private final BoomstickAimProgress aimProgress = new BoomstickAimProgress(0);

    private RecruitWeaponAdapters.Selection activeSelection;
    private int switchDelay;
    private int reloadTicksRemaining;
    private int reloadTicksTotal;
    private int reloadStepCount;
    private int reloadStepsStarted;
    private int reloadStepsDone;
    private boolean reloadStepsAborted;
    private int fireAnimationTicks;
    private BoomstickWeaponAdapter.ShotOutcome shotOutcome;
    private boolean navigationControlled;
    private UUID reservedTargetId;
    private long nextTargetScanTick;
    private BoomstickShotFacing.Snapshot formationFacingBeforeAim;
    private Vec3 firingFacingPosition;
    private LivingEntity retainedCombatTarget;

    public RecruitBoomstickAttackGoal(CrossBowmanEntity crossBowman, double speedModifier) {
        this(crossBowman, speedModifier, RecruitWeaponAdapters.production(), Mode.COMBAT);
    }

    public static RecruitBoomstickAttackGoal passiveReload(CrossBowmanEntity crossBowman) {
        return new RecruitBoomstickAttackGoal(
                crossBowman,
                0.0D,
                RecruitWeaponAdapters.production(),
                Mode.PASSIVE_RELOAD
        );
    }

    RecruitBoomstickAttackGoal(
            CrossBowmanEntity crossBowman,
            double speedModifier,
            RecruitWeaponAdapters adapters
    ) {
        this(crossBowman, speedModifier, adapters, Mode.COMBAT);
    }

    private RecruitBoomstickAttackGoal(
            CrossBowmanEntity crossBowman,
            double speedModifier,
            RecruitWeaponAdapters adapters,
            Mode mode
    ) {
        this.crossBowman = crossBowman;
        this.speedModifier = speedModifier;
        this.adapters = adapters;
        this.mode = mode;
        setFlags(mode == Mode.COMBAT
                ? EnumSet.of(Flag.LOOK, Flag.MOVE)
                : EnumSet.noneOf(Flag.class));
    }

    public BoomstickAttackState.Phase phase() {
        return state.phase();
    }

    @Override
    public boolean canUse() {
        if (!isOperational()
                || mustYieldToEmergencyMovement()
                || !hasUsableWeapon()) {
            return false;
        }
        // GoalSelector still polls this goal while its persistent cooldown keeps it stopped. Use
        // that poll to hand a retained live enemy back to Recruits instead of short-circuiting on
        // cooldown first and leaving the entity's visible target empty for the whole gap.
        boolean combatActive = commandAllowsCombat() && hasCombatPosition();
        if (isCooldownActive()) {
            return false;
        }
        ItemStack mainHandWeapon = crossBowman.getMainHandItem();
        // A loading window has exactly one owner. Whichever goal opened it drives its timer, its
        // native steps, and the marker the client renders from, so the other goal stays out until
        // the weapon reports itself done rather than opening a second window over the same weapon.
        if (adapters.findEnabled(mainHandWeapon)
                .map(adapter -> adapter.isReloading(mainHandWeapon))
                .orElse(false)) {
            return false;
        }
        return mode == Mode.COMBAT
                ? combatActive
                : !combatActive && mainHandWeaponNeedsReload();
    }

    @Override
    public boolean canContinueToUse() {
        if (!isOperational()
                || mustYieldToEmergencyMovement()) {
            return false;
        }
        if (fireAnimationTicks > 0) {
            return true;
        }
        // A reload outlives the target that prompted it. Tearing the goal down the moment an enemy
        // dies drops the weapon's reloading marker, so the recruit's arms fall and the passive goal
        // then opens a fresh window that starts the motion again from zero — a recruit that kills
        // its target mid-reload finishes that reload here instead. Combat mode holds the movement
        // flags, so a standing order to be somewhere else still ends it; the passive goal holds no
        // flags and blocks nothing, so it always sees its window through.
        if (state.phase() == BoomstickAttackState.Phase.RELOAD
                && (mode == Mode.PASSIVE_RELOAD || commandAllowsCombat())) {
            return true;
        }
        if (!adapters.isUsableEnabledWeapon(crossBowman, crossBowman.getMainHandItem())
                && !hasUsableWeaponInInventory()) {
            return false;
        }
        // Do not short-circuit target retention on cooldown: the visual facing can return to the
        // formation while the entity continues to remember whom it is fighting.
        boolean combatPosition = commandAllowsCombat() && hasCombatPosition();
        boolean cooldownActive = isCooldownActive();
        boolean combatActive = !cooldownActive && combatPosition;
        return mode == Mode.COMBAT
                ? combatActive
                : !combatActive && !cooldownActive && mainHandWeaponNeedsReload();
    }

    @Override
    public void start() {
        restoreFormationFacing();
        releasePendingShotReservation();
        state.reset();
        activeSelection = null;
        switchDelay = 0;
        reloadTicksRemaining = 0;
        reloadTicksTotal = 0;
        reloadStepCount = 0;
        reloadStepsStarted = 0;
        reloadStepsDone = 0;
        reloadStepsAborted = false;
        aimProgress.reset();
        fireAnimationTicks = 0;
        shotOutcome = null;
        navigationControlled = false;
        reservedTargetId = null;
        firingFacingPosition = null;
    }

    @Override
    public void stop() {
        resetAfterStop();
    }

    private void resetAfterStop() {
        if (CompatConfig.DEBUG_LOGGING.get()) {
            LOGGER.debug(
                    "Boomstick recruit {} [{}] goal torn down in phase {} with reloadTicksLeft={}",
                    crossBowman.getId(),
                    mode,
                    state.phase(),
                    reloadTicksRemaining);
        }
        if (activeSelection != null) {
            // A goal torn down between the shot and the end of its animation must not leave the
            // borrowed match in the recruit's hand.
            activeSelection.adapter().clearFiringTool(crossBowman);
        }
        endSteppedReloadIfActive();
        releasePendingShotReservation();
        restoreFormationFacing();
        clearWeaponAnimationState();
        state.reset();
        activeSelection = null;
        switchDelay = 0;
        reloadTicksRemaining = 0;
        reloadTicksTotal = 0;
        reloadStepCount = 0;
        reloadStepsStarted = 0;
        reloadStepsDone = 0;
        reloadStepsAborted = false;
        aimProgress.reset();
        fireAnimationTicks = 0;
        firingFacingPosition = null;
        shotOutcome = null;
        if (navigationControlled) {
            crossBowman.getNavigation().stop();
            navigationControlled = false;
        }
    }


    @Override
    public void tick() {
        if (!isOperational()) {
            stop();
            return;
        }
        if (mustYieldToEmergencyMovement()) {
            navigationControlled = false;
            resetAfterStop();
            return;
        }
        tickFireAnimation();
        if (switchDelay > 0) {
            switchDelay--;
            return;
        }

        ItemStack weapon = crossBowman.getMainHandItem();
        if (activeSelection != null && activeSelection.weapon() != weapon) {
            abortForWeaponChange(activeSelection);
        }
        Optional<RecruitWeaponAdapters.Selection> selected = adapters.selectEnabled(weapon, activeSelection);
        if (selected.isEmpty()) {
            activeSelection = null;
            switchToSupportedWeapon();
            return;
        }
        activeSelection = selected.orElseThrow();
        BoomstickWeaponAdapter adapter = activeSelection.adapter();

        // A slow projectile cannot cross the shared combat range, so each weapon reports how far its
        // own shot actually reaches and the recruit walks in rather than throwing short.
        double combatRange = effectiveCombatRange(adapter, weapon);
        boolean combatAllowed = mode == Mode.COMBAT && commandAllowsCombat();
        AimPoint aimPoint = combatAllowed ? findAimPoint(combatRange) : null;

        BoomstickWeaponProfile profile = adapter.profile(weapon).orElse(null);
        if (profile == null) {
            restoreFormationFacing();
            state.reset();
            return;
        }

        // The marker lives on the weapon stack, which means anything else holding that stack can put
        // it down: the item's own release path clears it outright, and a goal torn down elsewhere in
        // the same tick clears it on its way out. It is also the only thing a client can see a reload
        // through, so it is raised again for as long as the window is open rather than left to
        // whatever last touched the weapon.
        if (state.phase() == BoomstickAttackState.Phase.RELOAD && !adapter.isReloading(weapon)) {
            adapter.setReloading(weapon, true);
        }
        // The same argument applies to a wind-up that renders from the native use state: it is the
        // only thing a client can see that pose through, so it is held up for as long as the aim
        // window is open rather than left to whatever last touched the recruit.
        if (state.phase() == BoomstickAttackState.Phase.AIM
                && adapter.windUpUsesNativeItemState(weapon)
                && !crossBowman.isUsingItem()) {
            crossBowman.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        }

        BoomstickAttackState.Phase previous = state.phase();
        boolean ammoRequired = BoomstickAmmoAccess.isAmmoRequired();
        // A native chain that already spent components must be allowed to finish. Its ball is gone
        // by the middle step, so a plain inventory check would abandon a half-loaded weapon.
        boolean ammoAvailable = adapter.hasAmmo(crossBowman, weapon, ammoRequired)
                || steppedReloadInProgress();
        boolean loaded = adapter.isLoaded(weapon);
        boolean reloadComplete = advanceReloadTimer(previous);
        boolean cooldownComplete = !isCooldownActive();
        boolean fireTurnAvailable = reserveFireTurnIfNeeded(
                previous,
                aimPoint,
                weapon,
                profile,
                adapter,
                loaded,
                reloadComplete,
                cooldownComplete);
        AimPoint authorizedAimPoint = fireTurnAvailable ? aimPoint : null;
        boolean aimComplete = advanceAimTimer(previous, authorizedAimPoint);

        BoomstickAttackState.Signals signals = new BoomstickAttackState.Signals(
                isOperational(),
                authorizedAimPoint != null,
                true,
                loaded,
                ammoAvailable,
                reloadComplete,
                aimComplete,
                cooldownComplete,
                shotOutcome
        );
        BoomstickAttackState.Phase next = state.advance(signals);
        handleTransition(previous, next, weapon, profile, authorizedAimPoint, ammoRequired, adapter);
        if (next != BoomstickAttackState.Phase.AIM) {
            BoomstickThrowingAimFacing.untrack(crossBowman);
        }
        if (authorizedAimPoint != null || (combatAllowed && validTarget(crossBowman.getTarget()))) {
            moveAndLook(
                    authorizedAimPoint,
                    combatRange,
                    next == BoomstickAttackState.Phase.AIM,
                    next == BoomstickAttackState.Phase.AIM && adapter.isThrowable(weapon));
        }
    }

    private void handleTransition(
            BoomstickAttackState.Phase previous,
            BoomstickAttackState.Phase next,
            ItemStack weapon,
            BoomstickWeaponProfile profile,
            AimPoint aimPoint,
            boolean ammoRequired,
            BoomstickWeaponAdapter adapter
    ) {
        if (previous == next) {
            if (next == BoomstickAttackState.Phase.FIRE && shotOutcome == null) {
                fire(weapon, aimPoint, profile, adapter);
            }
            return;
        }
        if (CompatConfig.DEBUG_LOGGING.get()) {
            // Every phase edge, so a pose that appears and vanishes can be read back against the
            // state machine that drove it rather than guessed at from the screen.
            LOGGER.debug(
                    "Boomstick recruit {} [{}] phase {} becomes {}: reloading={} loaded={} reloadTicksLeft={}",
                    crossBowman.getId(),
                    mode,
                    previous,
                    next,
                    adapter.isReloading(weapon),
                    adapter.isLoaded(weapon),
                    reloadTicksRemaining);
        }
        // The wind-up is display state and belongs to the aim window alone, so it is raised on the
        // way in and dropped on every way out, including an aim the recruit never got to finish.
        if (previous == BoomstickAttackState.Phase.AIM) {
            adapter.setAiming(weapon, false);
            if (adapter.windUpUsesNativeItemState(weapon)) {
                // Dropped with the arm, including into the shot itself. This must stay
                // stopUsingItem: it clears the use state without running the item's own release
                // path, so no native throw can be reached. LivingEntity#releaseUsingItem is the
                // method that does run it and is not interchangeable here.
                crossBowman.stopUsingItem();
            }
            if (next != BoomstickAttackState.Phase.FIRE) {
                releasePendingShotReservation();
                restoreFormationFacing();
            }
        }

        if (next == BoomstickAttackState.Phase.RELOAD) {
            beginReload(weapon, profile, adapter);
        }
        if (previous == BoomstickAttackState.Phase.RELOAD
                && (next == BoomstickAttackState.Phase.AIM || next == BoomstickAttackState.Phase.IDLE)) {
            completeReload(weapon, ammoRequired, adapter);
        }
        if (next == BoomstickAttackState.Phase.AIM) {
            if (previous != BoomstickAttackState.Phase.AIM
                    && formationFacingBeforeAim == null) {
                formationFacingBeforeAim = BoomstickShotFacing.capture(crossBowman);
            }
            aimProgress.reset(adapter.aimTicks(weapon));
            adapter.setAiming(weapon, true);
            if (adapter.windUpUsesNativeItemState(weapon)) {
                crossBowman.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            shotOutcome = null;
        }
        if (next == BoomstickAttackState.Phase.FIRE) {
            fire(weapon, aimPoint, profile, adapter);
        }
        if (next == BoomstickAttackState.Phase.COOLDOWN) {
            shotOutcome = null;
        }
        if (next == BoomstickAttackState.Phase.IDLE
                || next == BoomstickAttackState.Phase.OUT_OF_AMMO) {
            releasePendingShotReservation();
            stopReloadAnimation(weapon, adapter);
        }
    }

    private void beginReload(
            ItemStack weapon,
            BoomstickWeaponProfile profile,
            BoomstickWeaponAdapter adapter
    ) {
        int fullReloadTicks = BoomstickCombatPolicy.reloadTicks(
                adapter.reloadTicks(weapon),
                crossBowman.isPassenger(),
                SupportedBoomsticks.ARBALEST_ID.equals(profile.registryId()));
        reloadStepCount = adapter.reloadStepCount(weapon);
        reloadStepsDone = Math.min(reloadStepCount, Math.max(0, adapter.completedReloadSteps(weapon)));
        reloadStepsStarted = reloadStepsDone;
        int remainingSteps = reloadStepCount - reloadStepsDone;
        reloadTicksRemaining = reloadStepCount > 0 && reloadStepsDone > 0
                ? Math.max(1, (int) Math.ceil(
                (double) fullReloadTicks * remainingSteps / reloadStepCount))
                : fullReloadTicks;
        reloadTicksTotal = reloadTicksRemaining;
        reloadStepsAborted = false;
        adapter.setReloading(weapon, true);
        adapter.setFiring(weapon, false);
        crossBowman.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        aimProgress.reset();
        if (CompatConfig.DEBUG_LOGGING.get()) {
            LOGGER.debug("Boomstick recruit {} started reload for {} ticks", crossBowman.getId(), reloadTicksRemaining);
        }
    }

    private void completeReload(
            ItemStack weapon,
            boolean ammoRequired,
            BoomstickWeaponAdapter adapter
    ) {
        stopReloadAnimation(weapon, adapter);
        if (reloadStepCount > 0) {
            // The native chain already spent its components and wrote the loaded state step by step.
            adapter.endSteppedReload(crossBowman, weapon);
            reloadStepCount = 0;
            reloadStepsStarted = 0;
            reloadStepsDone = 0;
            reloadStepsAborted = false;
            return;
        }
        if (adapter.consumeAmmo(crossBowman, weapon, ammoRequired)) {
            adapter.setLoaded(weapon, true);
        }
    }

    private void fire(
            ItemStack weapon,
            AimPoint aimPoint,
            BoomstickWeaponProfile profile,
            BoomstickWeaponAdapter adapter
    ) {
        if (aimPoint == null) {
            releasePendingShotReservation();
            restoreFormationFacing();
            shotOutcome = BoomstickWeaponAdapter.ShotOutcome.INVALID_TARGET;
            return;
        }
        try {
            int cooldownTicks = Math.max(0, adapter.cooldownTicks(weapon));
            // The match belongs to the shot, so it is in the hand before the shot goes off and gone
            // again with the fire animation.
            adapter.showFiringTool(crossBowman, weapon);
            Vec3 shotPosition = aimPoint.shotPosition();
            // Keep the visible weapon on the point tracked throughout the aim window. The
            // projectile deliberately targets lower on a living entity, but CROSSBOW_HOLD derives
            // both arm angles from the recruit's pitch; facing that low ballistic point here makes
            // a long firearm snap toward the ground for the shot and recoil.
            Vec3 facingPosition = aimPoint.position();
            BoomstickShotFacing.face(crossBowman, facingPosition);
            shotOutcome = adapter.fire(crossBowman, weapon, shotPosition).outcome();
            if (shotOutcome == BoomstickWeaponAdapter.ShotOutcome.FIRED
                    || shotOutcome == BoomstickWeaponAdapter.ShotOutcome.MISFIRED) {
                beginCooldown(cooldownTicks);
                if (shotOutcome == BoomstickWeaponAdapter.ShotOutcome.FIRED) {
                    commitShotReservation(aimPoint, profile);
                    firingFacingPosition = facingPosition;
                    fireAnimationTicks = FIRE_ANIMATION_TICKS;
                } else {
                    releasePendingShotReservation();
                    restoreFormationFacing();
                    adapter.clearFiringTool(crossBowman);
                }
            } else if (isRejectedShot(shotOutcome)) {
                releasePendingShotReservation();
                restoreFormationFacing();
                beginCooldown(REJECTED_SHOT_BACKOFF_TICKS);
                if (CompatConfig.DEBUG_LOGGING.get()) {
                    LOGGER.debug(
                            "Boomstick recruit {} backing off after a rejected {} shot for {}",
                            crossBowman.getId(),
                            shotOutcome,
                            profile.registryId());
                }
            } else {
                releasePendingShotReservation();
                restoreFormationFacing();
            }
        } catch (RuntimeException exception) {
            releasePendingShotReservation();
            restoreFormationFacing();
            shotOutcome = BoomstickWeaponAdapter.ShotOutcome.SPAWN_FAILED;
            LOGGER.error(
                    "Boomstick adapter failed for recruit {} with weapon {}",
                    crossBowman.getUUID(),
                    profile.registryId(),
                    exception
            );
        }
    }

    /**
     * Whether the adapter refused the shot itself rather than the target.
     *
     * <p>A refused target is normal and resolves on its own as the recruit or its enemy moves; a
     * refused weapon, load state, or spawn repeats identically until something else changes.</p>
     */
    private static boolean isRejectedShot(BoomstickWeaponAdapter.ShotOutcome outcome) {
        return outcome == BoomstickWeaponAdapter.ShotOutcome.INVALID_WEAPON
                || outcome == BoomstickWeaponAdapter.ShotOutcome.NOT_LOADED
                || outcome == BoomstickWeaponAdapter.ShotOutcome.SPAWN_FAILED;
    }

    private boolean advanceReloadTimer(BoomstickAttackState.Phase phase) {
        if (phase != BoomstickAttackState.Phase.RELOAD) {
            return false;
        }
        if (reloadTicksRemaining > 0) {
            reloadTicksRemaining--;
        }
        advanceReloadSteps();
        if (reloadStepsAborted) {
            // A missing or broken component ends the transaction instead of silently finishing it.
            return true;
        }
        return reloadTicksRemaining <= 0;
    }

    /**
     * Spreads the confirmed native loading steps evenly across the reload window.
     *
     * <p>Every step is committed before the window closes, so the weapon reaches its loaded stage
     * exactly when the timer runs out.</p>
     */
    private void advanceReloadSteps() {
        if (reloadStepCount <= 0 || reloadStepsAborted || activeSelection == null) {
            return;
        }
        int elapsed = reloadTicksTotal - reloadTicksRemaining;
        int remainingSteps = reloadStepCount - reloadStepsStarted;
        int due = reloadTicksTotal <= 0
                ? reloadStepCount
                : Math.min(
                        reloadStepCount,
                        reloadStepsStarted + (elapsed * remainingSteps) / reloadTicksTotal + 1);
        BoomstickWeaponAdapter adapter = activeSelection.adapter();
        ItemStack weapon = activeSelection.weapon();
        while (reloadStepsDone < due) {
            if (!adapter.applyReloadStep(crossBowman, weapon, reloadStepsDone)) {
                reloadStepsAborted = true;
                adapter.endSteppedReload(crossBowman, weapon);
                if (CompatConfig.DEBUG_LOGGING.get()) {
                    LOGGER.debug(
                            "Boomstick recruit {} aborted reload at step {}",
                            crossBowman.getId(),
                            reloadStepsDone);
                }
                return;
            }
            reloadStepsDone++;
        }
    }

    private boolean advanceAimTimer(BoomstickAttackState.Phase phase, AimPoint aimPoint) {
        if (phase != BoomstickAttackState.Phase.AIM) {
            aimProgress.reset();
            return false;
        }
        return aimProgress.advance(aimPoint == null ? null : aimPoint.identity());
    }


    private void switchToSupportedWeapon() {
        if (!hasUsableWeaponInInventory()) {
            return;
        }
        // Not Recruits' own switchMainHandItem: it starts its scan past both hand slots, so it cannot
        // see a weapon the carry order parked in the off hand. Firing and the native loading chain
        // both need the weapon in the main hand — the chain borrows the off hand for its own tools.
        RecruitHandSwap.intoMainHand(
                crossBowman,
                stack -> adapters.isUsableEnabledWeapon(crossBowman, stack));
        state.reset();
        activeSelection = null;
        switchDelay = 1;
    }

    private void abortForWeaponChange(RecruitWeaponAdapters.Selection previous) {
        previous.adapter().endSteppedReload(crossBowman, previous.weapon());
        previous.adapter().clearTransientState(previous.weapon());
        crossBowman.stopUsingItem();
        activeSelection = null;
        releasePendingShotReservation();
        restoreFormationFacing();
        state.reset();
        shotOutcome = null;
        aimProgress.reset();
        reloadTicksRemaining = 0;
        reloadTicksTotal = 0;
        reloadStepCount = 0;
        reloadStepsStarted = 0;
        reloadStepsDone = 0;
        reloadStepsAborted = false;
        fireAnimationTicks = 0;
    }

    /** Whether a native loading chain has already committed at least one step. */
    private boolean steppedReloadInProgress() {
        return reloadStepCount > 0 && reloadStepsDone > 0 && !reloadStepsAborted;
    }

    /**
     * Claims only as much simultaneous formation fire as the target can plausibly survive.
     * Reloading does not claim a place: a recruit joins the firing line only when its weapon is
     * loaded and it is ready to begin or continue the actual aim window.
     */
    private boolean reserveFireTurnIfNeeded(
            BoomstickAttackState.Phase phase,
            AimPoint aimPoint,
            ItemStack weapon,
            BoomstickWeaponProfile profile,
            BoomstickWeaponAdapter adapter,
            boolean loaded,
            boolean reloadComplete,
            boolean cooldownComplete
    ) {
        if (aimPoint == null) {
            releasePendingShotReservation();
            return false;
        }
        LivingEntity target = aimPoint.entity();
        if (target == null) {
            releasePendingShotReservation();
            return true;
        }
        if (!needsFireTurnBeforeAim(phase, loaded, reloadComplete, cooldownComplete)) {
            releasePendingShotReservation();
            return true;
        }

        UUID targetId = target.getUUID();
        if (reservedTargetId != null && !reservedTargetId.equals(targetId)) {
            releasePendingShotReservation();
        }
        double expectedDamage;
        try {
            expectedDamage = adapter.estimatedVolleyDamage(weapon);
        } catch (RuntimeException | LinkageError ignored) {
            expectedDamage = Math.max(1, profile.projectileCount());
        }
        boolean selfDefense = isDefendingAgainst(target);
        boolean reserved = BoomstickFireCoordinator.reserveShared(
                crossBowman.getUUID(),
                targetId,
                target.getHealth() + target.getAbsorptionAmount(),
                expectedDamage,
                profile.projectileCount(),
                crossBowman.level().getGameTime(),
                AIM_RESERVATION_LEASE_TICKS,
                selfDefense);
        if (reserved) {
            reservedTargetId = targetId;
        } else if (!selfDefense) {
            // Comrades already own this kill. Look for an enemy nobody has covered rather than
            // waiting out their reload behind a target that is dead in all but name.
            retargetAwayFromCoveredEnemy(
                    target,
                    effectiveCombatRange(adapter, weapon),
                    profile.projectileCount());
        }
        return reserved;
    }

    /**
     * Moves a recruit the formation has no shot left for onto the nearest enemy that still needs one.
     *
     * <p>The reservation ledger only prevents overkill; on its own it leaves the surplus rank idle
     * until Recruits' own target goals happen to hand it something else. This closes that gap, and
     * pays for it carefully: it runs only after a refused reservation, on a staggered interval, over
     * a range capped below the shared combat maximum, and it spends a line-of-sight ray only on a
     * candidate that is both closer than the current best and still has reservation capacity.</p>
     */
    private void retargetAwayFromCoveredEnemy(
            LivingEntity coveredTarget,
            double combatRange,
            int projectileCount
    ) {
        long gameTime = crossBowman.level().getGameTime();
        if (gameTime < nextTargetScanTick) {
            return;
        }
        nextTargetScanTick = BoomstickCombatPolicy.nextTargetScanTick(
                gameTime,
                TARGET_SCAN_INTERVAL_TICKS,
                TARGET_SCAN_STAGGER_TICKS,
                crossBowman.getId());

        double searchRange = Math.min(combatRange, MAX_TARGET_SCAN_RANGE);
        if (searchRange <= 0.0D) {
            return;
        }
        List<LivingEntity> candidates = crossBowman.level().getEntitiesOfClass(
                LivingEntity.class,
                crossBowman.getBoundingBox().inflate(searchRange),
                candidate -> candidate != coveredTarget && validTarget(candidate));

        LivingEntity best = null;
        double bestDistanceSq = searchRange * searchRange;
        int lineOfSightChecks = 0;
        for (LivingEntity candidate : candidates) {
            double distanceSq = crossBowman.distanceToSqr(candidate);
            if (distanceSq >= bestDistanceSq) {
                continue;
            }
            if (!BoomstickFireCoordinator.hasFireTurnCapacityShared(
                    candidate.getUUID(),
                    candidate.getHealth() + candidate.getAbsorptionAmount(),
                    projectileCount,
                    gameTime)) {
                continue;
            }
            if (lineOfSightChecks >= MAX_TARGET_SCAN_LINE_OF_SIGHT_CHECKS) {
                break;
            }
            lineOfSightChecks++;
            if (!crossBowman.hasLineOfSight(candidate)) {
                continue;
            }
            best = candidate;
            bestDistanceSq = distanceSq;
        }
        if (best != null) {
            retainedCombatTarget = best;
            crossBowman.setTarget(best);
        }
    }

    /** Prevents any transition into the visible aim window before formation fire authorizes it. */
    private static boolean needsFireTurnBeforeAim(
            BoomstickAttackState.Phase phase,
            boolean loaded,
            boolean reloadComplete,
            boolean cooldownComplete
    ) {
        return switch (phase) {
            case AIM -> true;
            case IDLE, ACQUIRE_WEAPON, OUT_OF_AMMO -> loaded;
            case RELOAD -> reloadComplete;
            case COOLDOWN -> loaded && cooldownComplete;
            case FIRE -> false;
        };
    }

    private void commitShotReservation(AimPoint aimPoint, BoomstickWeaponProfile profile) {
        if (reservedTargetId == null || aimPoint.entity() == null) {
            return;
        }
        long gameTime = crossBowman.level().getGameTime();
        double distance = crossBowman.getEyePosition(1.0F).distanceTo(aimPoint.shotPosition());
        double velocity = Math.max(0.1D, profile.projectileVelocity());
        int flightTicks = (int) Math.ceil(distance / velocity) + 10;
        flightTicks = Math.max(
                MIN_PROJECTILE_RESERVATION_TICKS,
                Math.min(MAX_PROJECTILE_RESERVATION_TICKS, flightTicks));
        BoomstickFireCoordinator.commitShared(
                crossBowman.getUUID(),
                reservedTargetId,
                gameTime,
                flightTicks);
        // The committed entry remains in the shared ledger until impact or expiry; this goal no
        // longer owns it and therefore must not remove it during its normal cooldown teardown.
        reservedTargetId = null;
    }

    private void releasePendingShotReservation() {
        if (reservedTargetId == null) {
            return;
        }
        BoomstickFireCoordinator.releaseShared(crossBowman.getUUID(), reservedTargetId);
        reservedTargetId = null;
    }

    /** Restores a borrowed off hand when the goal ends mid-transaction. */
    private void endSteppedReloadIfActive() {
        if (reloadStepCount <= 0 || activeSelection == null) {
            return;
        }
        activeSelection.adapter().endSteppedReload(crossBowman, activeSelection.weapon());
    }

    private void beginCooldown(int cooldownTicks) {
        if (cooldownTicks == 0) {
            crossBowman.getPersistentData().remove(COOLDOWN_UNTIL_TAG);
            return;
        }
        crossBowman.getPersistentData().putLong(
                COOLDOWN_UNTIL_TAG,
                crossBowman.level().getGameTime() + cooldownTicks
        );
    }

    private boolean isCooldownActive() {
        long cooldownUntil = crossBowman.getPersistentData().getLong(COOLDOWN_UNTIL_TAG);
        if (cooldownUntil <= crossBowman.level().getGameTime()) {
            crossBowman.getPersistentData().remove(COOLDOWN_UNTIL_TAG);
            return false;
        }
        return true;
    }

    private void stopReloadAnimation(ItemStack weapon, BoomstickWeaponAdapter adapter) {
        adapter.setReloading(weapon, false);
        crossBowman.stopUsingItem();
    }

    private void clearWeaponAnimationState() {
        ItemStack weapon = crossBowman.getMainHandItem();
        if (activeSelection != null) {
            activeSelection.adapter().clearTransientState(activeSelection.weapon());
        } else if (CompatConfig.ENABLED.get()) {
            adapters.findEnabled(weapon).ifPresent(adapter -> adapter.clearTransientState(weapon));
        }
        crossBowman.stopUsingItem();
    }

    private void tickFireAnimation() {
        if (fireAnimationTicks <= 0) {
            return;
        }
        if (firingFacingPosition != null) {
            // Hold the direction of the committed shot, not the moving target. The recruit therefore
            // does not track or spin during recoil, then snaps back to its saved formation heading.
            BoomstickShotFacing.face(crossBowman, firingFacingPosition);
        }
        fireAnimationTicks--;
        if (fireAnimationTicks == 0) {
            firingFacingPosition = null;
            restoreFormationFacing();
            if (activeSelection != null) {
                activeSelection.adapter().setFiring(activeSelection.weapon(), false);
                activeSelection.adapter().clearFiringTool(crossBowman);
            }
        }
    }

    private boolean isOperational() {
        return !crossBowman.level().isClientSide
                && crossBowman.isAlive()
                && CompatConfig.ENABLED.get()
                && adapters.hasEnabledAdapter()
                && crossBowman.getShouldRanged()
                && !crossBowman.getShouldRest();
    }

    private boolean mustYieldToEmergencyMovement() {
        return crossBowman.getFleeing()
                || !crossBowman.level().getEntitiesOfClass(
                        PrimedTnt.class,
                        crossBowman.getBoundingBox().inflate(EMERGENCY_TNT_RADIUS)
                ).isEmpty();
    }

    private boolean commandAllowsCombat() {
        if (crossBowman.getShouldHoldPos() && crossBowman.getHoldPos() != null) {
            if (crossBowman.position().distanceToSqr(crossBowman.getHoldPos()) > HOLD_POSITION_RADIUS * HOLD_POSITION_RADIUS) {
                return false;
            }
        }
        if (crossBowman.getShouldMovePos() && crossBowman.getMovePos() != null) {
            if (crossBowman.blockPosition().distSqr(crossBowman.getMovePos()) > 16L) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCombatPosition() {
        return combatTarget() != null || hasStrategicFirePosition();
    }

    private boolean hasUsableWeapon() {
        return adapters.isUsableEnabledWeapon(crossBowman, crossBowman.getMainHandItem())
                || hasUsableWeaponInInventory();
    }

    private boolean hasUsableWeaponInInventory() {
        ItemStack matching = crossBowman.getMatchingItem(
                stack -> adapters.isUsableEnabledWeapon(crossBowman, stack));
        return matching != null && !matching.isEmpty();
    }

    private boolean mainHandWeaponNeedsReload() {
        ItemStack weapon = crossBowman.getMainHandItem();
        return adapters.findEnabled(weapon)
                .map(adapter -> !adapter.isLoaded(weapon)
                        && adapter.hasAmmo(crossBowman, weapon, BoomstickAmmoAccess.isAmmoRequired()))
                .orElse(false);
    }

    /** Clamps the shared combat range to what this weapon's own projectile can actually reach. */
    private double effectiveCombatRange(BoomstickWeaponAdapter adapter, ItemStack weapon) {
        return BoomstickCombatPolicy.clampCombatRange(
                adapter.effectiveRange(weapon, MAX_COMBAT_RANGE),
                MAX_COMBAT_RANGE);
    }

    private AimPoint findAimPoint(double combatRange) {
        LivingEntity target = combatTarget();
        if (target != null) {
            if (crossBowman.hasLineOfSight(target)
                    && BoomstickCombatPolicy.isWithinCombatRange(
                            crossBowman.distanceToSqr(target),
                            combatRange)) {
                return new AimPoint(target.getEyePosition(1.0F), target);
            }
            return null;
        }
        if (BoomstickCombatPolicy.shouldUseStrategicFire(false, hasStrategicFirePosition())) {
            return new AimPoint(crossBowman.getStrategicFirePos().getCenter(), null);
        }
        return null;
    }

    /**
     * Keeps the combat decision separate from the visible look direction.
     *
     * <p>The boomstick goal deliberately releases LOOK and MOVE during its persistent cooldown so
     * formation and emergency goals can run. Recruits' ordinary target goals may clear their live
     * target in that gap. A shot must not turn that short scheduling gap into amnesia: retain the
     * last still-valid enemy and hand it back when this goal is next queried. Dead, allied, or
     * otherwise unattackable targets are discarded normally.</p>
     */
    private LivingEntity combatTarget() {
        LivingEntity current = crossBowman.getTarget();
        if (validTarget(current)) {
            retainedCombatTarget = current;
            return current;
        }
        if (!validTarget(retainedCombatTarget)) {
            retainedCombatTarget = null;
            return null;
        }
        crossBowman.setTarget(retainedCombatTarget);
        return retainedCombatTarget;
    }

    /**
     * Whether the target is fighting this recruit rather than the other way round.
     *
     * <p>A mob that has aggroed on this recruit, or that has hit it recently, counts. Vanilla clears
     * the last hurt entry a hundred ticks after the blow, so the window closes on its own once the
     * enemy walks away.</p>
     */
    private boolean isDefendingAgainst(LivingEntity target) {
        boolean aggroed = target instanceof Mob mob && mob.getTarget() == crossBowman;
        return BoomstickCombatPolicy.allowsSelfDefenseFire(
                aggroed,
                target.getLastHurtMob() == crossBowman);
    }

    /**
     * Whether this recruit may fight the given enemy at all.
     *
     * <p>The aggro state is checked here rather than at the goal's entry points because the goal
     * hands targets back to the entity — it restores a retained enemy across its own cooldown and
     * picks a fresh one when the formation has already covered the current one. A passive recruit
     * whose target Recruits just cleared would have that target written straight back on the next
     * tick, so the order has to hold at the point where a target is accepted.</p>
     */
    private boolean validTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && target != crossBowman
                && !crossBowman.isAlliedTo(target)
                && BoomstickCombatPolicy.allowsEntityCombat(crossBowman.getState())
                && crossBowman.canAttack(target);
    }

    private boolean hasStrategicFirePosition() {
        return CompatConfig.ALLOW_STRATEGIC_FIRE.get()
                && crossBowman.getShouldStrategicFire()
                && crossBowman.getStrategicFirePos() != null;
    }

    private void moveAndLook(
            AimPoint aimPoint,
            double combatRange,
            boolean trackAimPoint,
            boolean lockBodyToAim
    ) {
        navigationControlled = true;
        LivingEntity target = aimPoint == null ? combatTarget() : aimPoint.entity();
        if (trackAimPoint && aimPoint != null && lockBodyToAim) {
            // Formation steering can overwrite LookControl's body yaw. A raised throwing arm must
            // remain visibly aligned with the reserved target for the full wind-up, not only when
            // the projectile leaves the recruit's hand.
            Vec3 shotPosition = aimPoint.shotPosition();
            BoomstickShotFacing.face(crossBowman, shotPosition);
            BoomstickThrowingAimFacing.track(
                    crossBowman,
                    shotPosition,
                    aimPoint.entity() == null ? null : aimPoint.entity().getUUID());
        } else if (trackAimPoint && aimPoint != null) {
            Vec3 position = aimPoint.position();
            crossBowman.getLookControl().setLookAt(position.x, position.y, position.z, 30.0F, 30.0F);
        } else if (trackAimPoint && validTarget(target)) {
            crossBowman.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (!validTarget(target)) {
            crossBowman.getNavigation().stop();
            return;
        }
        boolean hasLineOfSight = crossBowman.hasLineOfSight(target);
        if (BoomstickCombatPolicy.shouldApproachTarget(
                hasLineOfSight,
                crossBowman.distanceToSqr(target),
                combatRange)) {
            crossBowman.getNavigation().moveTo(target, speedModifier);
        } else {
            crossBowman.getNavigation().stop();
        }
    }

    private void restoreFormationFacing() {
        BoomstickThrowingAimFacing.untrack(crossBowman);
        if (formationFacingBeforeAim == null) {
            return;
        }
        BoomstickShotFacing.restore(crossBowman, formationFacingBeforeAim);
        formationFacingBeforeAim = null;
        firingFacingPosition = null;
    }


    private record AimPoint(Vec3 position, LivingEntity entity) {
        private Vec3 shotPosition() {
            return entity == null
                    ? position
                    : new Vec3(entity.getX(), entity.getY(1.0D / 3.0D), entity.getZ());
        }

        private Object identity() {
            return entity == null ? position : entity.getUUID();
        }
    }
}
