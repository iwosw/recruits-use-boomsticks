package org.iwoss.recruits_use_boomsticks.ai;

import com.mojang.logging.LogUtils;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
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
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.Optional;

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
    private int reloadStepsDone;
    private boolean reloadStepsAborted;
    private int fireAnimationTicks;
    private BoomstickWeaponAdapter.ShotOutcome shotOutcome;
    private boolean navigationControlled;

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
                || !hasSupportedWeapon()
                || isCooldownActive()) {
            return false;
        }
        ItemStack mainHandWeapon = crossBowman.getMainHandItem();
        if (mode == Mode.COMBAT && adapters.findEnabled(mainHandWeapon)
                .map(adapter -> adapter.isReloading(mainHandWeapon))
                .orElse(false)) {
            return false;
        }
        boolean combatActive = commandAllowsCombat() && hasCombatPosition();
        return mode == Mode.COMBAT
                ? combatActive
                : !combatActive && mainHandWeaponNeedsReload();
    }

    @Override
    public boolean canContinueToUse() {
        if (!isOperational()
                || mustYieldToEmergencyMovement()
                || (!adapters.isSupportedEnabledWeapon(crossBowman.getMainHandItem())
                && !hasSupportedWeaponInInventory())) {
            return false;
        }
        if (fireAnimationTicks > 0) {
            return true;
        }
        if (mode == Mode.PASSIVE_RELOAD && state.phase() == BoomstickAttackState.Phase.RELOAD) {
            return true;
        }
        boolean cooldownActive = isCooldownActive();
        boolean combatActive = !cooldownActive && commandAllowsCombat() && hasCombatPosition();
        return mode == Mode.COMBAT
                ? combatActive
                : !combatActive && !cooldownActive && mainHandWeaponNeedsReload();
    }

    @Override
    public void start() {
        state.reset();
        activeSelection = null;
        switchDelay = 0;
        reloadTicksRemaining = 0;
        reloadTicksTotal = 0;
        reloadStepCount = 0;
        reloadStepsDone = 0;
        reloadStepsAborted = false;
        aimProgress.reset();
        fireAnimationTicks = 0;
        shotOutcome = null;
        navigationControlled = false;
    }

    @Override
    public void stop() {
        resetAfterStop();
    }

    private void resetAfterStop() {
        endSteppedReloadIfActive();
        clearWeaponAnimationState();
        state.reset();
        activeSelection = null;
        switchDelay = 0;
        reloadTicksRemaining = 0;
        reloadTicksTotal = 0;
        reloadStepCount = 0;
        reloadStepsDone = 0;
        reloadStepsAborted = false;
        aimProgress.reset();
        fireAnimationTicks = 0;
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
        if (aimPoint != null || (combatAllowed && validTarget(crossBowman.getTarget()))) {
            moveAndLook(aimPoint, combatRange);
        }

        BoomstickWeaponProfile profile = adapter.profile(weapon).orElse(null);
        if (profile == null) {
            state.reset();
            return;
        }

        BoomstickAttackState.Phase previous = state.phase();
        boolean ammoRequired = BoomstickAmmoAccess.isAmmoRequired();
        // A native chain that already spent components must be allowed to finish. Its ball is gone
        // by the middle step, so a plain inventory check would abandon a half-loaded weapon.
        boolean ammoAvailable = adapter.hasAmmo(crossBowman, weapon, ammoRequired)
                || steppedReloadInProgress();
        boolean loaded = adapter.isLoaded(weapon);
        boolean reloadComplete = advanceReloadTimer(previous);
        boolean aimComplete = advanceAimTimer(previous, aimPoint);
        boolean cooldownComplete = !isCooldownActive();

        BoomstickAttackState.Signals signals = new BoomstickAttackState.Signals(
                isOperational(),
                aimPoint != null,
                true,
                loaded,
                ammoAvailable,
                reloadComplete,
                aimComplete,
                cooldownComplete,
                shotOutcome
        );
        BoomstickAttackState.Phase next = state.advance(signals);
        handleTransition(previous, next, weapon, profile, aimPoint, ammoRequired, adapter);
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
        // The wind-up is display state and belongs to the aim window alone, so it is raised on the
        // way in and dropped on every way out, including an aim the recruit never got to finish.
        if (previous == BoomstickAttackState.Phase.AIM) {
            adapter.setAiming(weapon, false);
        }

        if (next == BoomstickAttackState.Phase.RELOAD) {
            beginReload(weapon, profile, adapter);
        }
        if (previous == BoomstickAttackState.Phase.RELOAD
                && (next == BoomstickAttackState.Phase.AIM || next == BoomstickAttackState.Phase.IDLE)) {
            completeReload(weapon, ammoRequired, adapter);
        }
        if (next == BoomstickAttackState.Phase.AIM) {
            aimProgress.reset(adapter.aimTicks(weapon));
            adapter.setAiming(weapon, true);
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
            stopReloadAnimation(weapon, adapter);
        }
    }

    private void beginReload(
            ItemStack weapon,
            BoomstickWeaponProfile profile,
            BoomstickWeaponAdapter adapter
    ) {
        reloadTicksRemaining = BoomstickCombatPolicy.reloadTicks(
                adapter.reloadTicks(weapon),
                crossBowman.isPassenger(),
                SupportedBoomsticks.ARBALEST_ID.equals(profile.registryId()));
        reloadTicksTotal = reloadTicksRemaining;
        reloadStepCount = adapter.reloadStepCount(weapon);
        reloadStepsDone = 0;
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
            shotOutcome = BoomstickWeaponAdapter.ShotOutcome.INVALID_TARGET;
            return;
        }
        try {
            int cooldownTicks = Math.max(0, adapter.cooldownTicks(weapon));
            shotOutcome = adapter.fire(crossBowman, weapon, aimPoint.shotPosition()).outcome();
            if (shotOutcome == BoomstickWeaponAdapter.ShotOutcome.FIRED
                    || shotOutcome == BoomstickWeaponAdapter.ShotOutcome.MISFIRED) {
                beginCooldown(cooldownTicks);
                if (shotOutcome == BoomstickWeaponAdapter.ShotOutcome.FIRED) {
                    fireAnimationTicks = FIRE_ANIMATION_TICKS;
                }
            } else if (isRejectedShot(shotOutcome)) {
                beginCooldown(REJECTED_SHOT_BACKOFF_TICKS);
                if (CompatConfig.DEBUG_LOGGING.get()) {
                    LOGGER.debug(
                            "Boomstick recruit {} backing off after a rejected {} shot for {}",
                            crossBowman.getId(),
                            shotOutcome,
                            profile.registryId());
                }
            }
        } catch (RuntimeException exception) {
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
        int due = reloadTicksTotal <= 0
                ? reloadStepCount
                : Math.min(reloadStepCount, (elapsed * reloadStepCount) / reloadTicksTotal + 1);
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
        if (!hasSupportedWeaponInInventory()) {
            return;
        }
        crossBowman.switchMainHandItem(adapters::isSupportedEnabledWeapon);
        state.reset();
        activeSelection = null;
        switchDelay = 1;
    }

    private void abortForWeaponChange(RecruitWeaponAdapters.Selection previous) {
        previous.adapter().endSteppedReload(crossBowman, previous.weapon());
        previous.adapter().clearTransientState(previous.weapon());
        crossBowman.stopUsingItem();
        activeSelection = null;
        state.reset();
        shotOutcome = null;
        aimProgress.reset();
        reloadTicksRemaining = 0;
        reloadTicksTotal = 0;
        reloadStepCount = 0;
        reloadStepsDone = 0;
        reloadStepsAborted = false;
        fireAnimationTicks = 0;
    }

    /** Whether a native loading chain has already committed at least one step. */
    private boolean steppedReloadInProgress() {
        return reloadStepCount > 0 && reloadStepsDone > 0 && !reloadStepsAborted;
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
        fireAnimationTicks--;
        if (fireAnimationTicks == 0 && activeSelection != null) {
            activeSelection.adapter().setFiring(activeSelection.weapon(), false);
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
        return validTarget(crossBowman.getTarget()) || hasStrategicFirePosition();
    }

    private boolean hasSupportedWeapon() {
        return adapters.isSupportedEnabledWeapon(crossBowman.getMainHandItem()) || hasSupportedWeaponInInventory();
    }

    private boolean hasSupportedWeaponInInventory() {
        ItemStack matching = crossBowman.getMatchingItem(adapters::isSupportedEnabledWeapon);
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
        LivingEntity target = crossBowman.getTarget();
        boolean validCombatTarget = validTarget(target);
        if (validCombatTarget) {
            if (crossBowman.hasLineOfSight(target)
                    && BoomstickCombatPolicy.isWithinCombatRange(
                            crossBowman.distanceToSqr(target),
                            combatRange)) {
                return new AimPoint(target.getEyePosition(1.0F), target);
            }
            return null;
        }
        if (BoomstickCombatPolicy.shouldUseStrategicFire(validCombatTarget, hasStrategicFirePosition())) {
            return new AimPoint(crossBowman.getStrategicFirePos().getCenter(), null);
        }
        return null;
    }

    private boolean validTarget(LivingEntity target) {
        return target != null
                && target.isAlive()
                && target != crossBowman
                && !crossBowman.isAlliedTo(target)
                && crossBowman.canAttack(target);
    }

    private boolean hasStrategicFirePosition() {
        return CompatConfig.ALLOW_STRATEGIC_FIRE.get()
                && crossBowman.getShouldStrategicFire()
                && crossBowman.getStrategicFirePos() != null;
    }

    private void moveAndLook(AimPoint aimPoint, double combatRange) {
        navigationControlled = true;
        LivingEntity target = aimPoint == null ? crossBowman.getTarget() : aimPoint.entity();
        if (aimPoint != null) {
            Vec3 position = aimPoint.position();
            crossBowman.getLookControl().setLookAt(position.x, position.y, position.z, 30.0F, 30.0F);
        } else if (validTarget(target)) {
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
