package org.iwoss.recruits_use_boomsticks.ai;

import com.mojang.logging.LogUtils;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickAmmoAccess;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponAdapter;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickWeaponProfile;
import org.iwoss.recruits_use_boomsticks.compat.MedievalBoomsticksAdapter;
import org.iwoss.recruits_use_boomsticks.compat.SupportedBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.Optional;

/** Ranged goal for supported Medieval Boomsticks weapons on Recruits crossbowmen. */
public final class RecruitBoomstickAttackGoal extends Goal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int AIM_WINDOW_TICKS = 12;
    private static final int FIRE_ANIMATION_TICKS = 3;
    private static final double MAX_COMBAT_RANGE = 45.0D;
    private static final double HOLD_POSITION_RADIUS = 4.0D;

    private final CrossBowmanEntity crossBowman;
    private final double speedModifier;
    private final BoomstickWeaponAdapter adapter;
    private final BoomstickAttackState state = new BoomstickAttackState();

    private ItemStack activeWeapon;
    private int switchDelay;
    private int reloadTicksRemaining;
    private int aimTicks;
    private int cooldownTicksRemaining;
    private int fireAnimationTicks;
    private BoomstickWeaponAdapter.ShotOutcome shotOutcome;
    private boolean navigationControlled;

    public RecruitBoomstickAttackGoal(CrossBowmanEntity crossBowman, double speedModifier) {
        this(crossBowman, speedModifier, MedievalBoomsticksAdapter.INSTANCE);
    }

    RecruitBoomstickAttackGoal(
            CrossBowmanEntity crossBowman,
            double speedModifier,
            BoomstickWeaponAdapter adapter
    ) {
        this.crossBowman = crossBowman;
        this.speedModifier = speedModifier;
        this.adapter = adapter;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    public BoomstickAttackState.Phase phase() {
        return state.phase();
    }

    @Override
    public boolean canUse() {
        return isOperational()
                && hasSupportedWeapon()
                && ((commandAllowsCombat() && hasCombatPosition()) || mainHandWeaponNeedsReload());
    }

    @Override
    public boolean canContinueToUse() {
        return isOperational()
                && (adapter.supports(crossBowman.getMainHandItem()) || hasSupportedWeaponInInventory())
                && ((commandAllowsCombat() && hasCombatPosition()) || mainHandWeaponNeedsReload());
    }

    @Override
    public void start() {
        state.reset();
        activeWeapon = null;
        switchDelay = 0;
        reloadTicksRemaining = 0;
        aimTicks = 0;
        cooldownTicksRemaining = 0;
        fireAnimationTicks = 0;
        shotOutcome = null;
        navigationControlled = false;
    }

    @Override
    public void stop() {
        clearWeaponAnimationState();
        state.reset();
        activeWeapon = null;
        switchDelay = 0;
        reloadTicksRemaining = 0;
        aimTicks = 0;
        cooldownTicksRemaining = 0;
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
        tickFireAnimation();
        if (switchDelay > 0) {
            switchDelay--;
            return;
        }

        ItemStack weapon = crossBowman.getMainHandItem();
        if (!adapter.supports(weapon)) {
            switchToSupportedWeapon();
            return;
        }
        if (activeWeapon != null && activeWeapon != weapon) {
            abortForWeaponChange(activeWeapon);
        }
        activeWeapon = weapon;

        boolean combatAllowed = commandAllowsCombat();
        AimPoint aimPoint = combatAllowed ? findAimPoint() : null;
        if (aimPoint != null || (combatAllowed && validTarget(crossBowman.getTarget()))) {
            moveAndLook(aimPoint);
        }

        BoomstickWeaponProfile profile = adapter.profile(weapon).orElse(null);
        if (profile == null) {
            state.reset();
            return;
        }

        BoomstickAttackState.Phase previous = state.phase();
        boolean ammoRequired = BoomstickAmmoAccess.isAmmoRequired();
        boolean ammoAvailable = adapter.hasAmmo(crossBowman, weapon, ammoRequired);
        boolean loaded = adapter.isLoaded(weapon);
        boolean reloadComplete = advanceReloadTimer(previous);
        boolean aimComplete = advanceAimTimer(previous, aimPoint != null);
        boolean cooldownComplete = advanceCooldownTimer(previous);

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
        handleTransition(previous, next, weapon, profile, aimPoint, ammoRequired);
    }

    private void handleTransition(
            BoomstickAttackState.Phase previous,
            BoomstickAttackState.Phase next,
            ItemStack weapon,
            BoomstickWeaponProfile profile,
            AimPoint aimPoint,
            boolean ammoRequired
    ) {
        if (previous == next) {
            if (next == BoomstickAttackState.Phase.FIRE && shotOutcome == null) {
                fire(weapon, aimPoint, profile);
            }
            return;
        }

        if (next == BoomstickAttackState.Phase.RELOAD) {
            beginReload(weapon, profile);
        }
        if (previous == BoomstickAttackState.Phase.RELOAD
                && (next == BoomstickAttackState.Phase.AIM || next == BoomstickAttackState.Phase.IDLE)) {
            completeReload(weapon, ammoRequired);
        }
        if (next == BoomstickAttackState.Phase.AIM) {
            aimTicks = 0;
            shotOutcome = null;
        }
        if (next == BoomstickAttackState.Phase.FIRE) {
            fire(weapon, aimPoint, profile);
        }
        if (next == BoomstickAttackState.Phase.COOLDOWN) {
            cooldownTicksRemaining = Math.max(0, profile.cooldownTicks());
            shotOutcome = null;
        }
        if (next == BoomstickAttackState.Phase.IDLE
                || next == BoomstickAttackState.Phase.OUT_OF_AMMO) {
            stopReloadAnimation(weapon);
        }
    }

    private void beginReload(ItemStack weapon, BoomstickWeaponProfile profile) {
        reloadTicksRemaining = Math.max(1, adapter.reloadTicks(weapon));
        adapter.setReloading(weapon, true);
        adapter.setFiring(weapon, false);
        crossBowman.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        aimTicks = 0;
        if (CompatConfig.DEBUG_LOGGING.get()) {
            LOGGER.debug("Boomstick recruit {} started reload for {} ticks", crossBowman.getId(), reloadTicksRemaining);
        }
    }

    private void completeReload(ItemStack weapon, boolean ammoRequired) {
        stopReloadAnimation(weapon);
        if (adapter.consumeAmmo(crossBowman, weapon, ammoRequired)) {
            adapter.setLoaded(weapon, true);
        }
    }

    private void fire(ItemStack weapon, AimPoint aimPoint, BoomstickWeaponProfile profile) {
        if (aimPoint == null) {
            shotOutcome = BoomstickWeaponAdapter.ShotOutcome.INVALID_TARGET;
            return;
        }
        try {
            shotOutcome = adapter.fire(crossBowman, weapon, aimPoint.position()).outcome();
            if (shotOutcome == BoomstickWeaponAdapter.ShotOutcome.FIRED) {
                fireAnimationTicks = FIRE_ANIMATION_TICKS;
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

    private boolean advanceReloadTimer(BoomstickAttackState.Phase phase) {
        if (phase != BoomstickAttackState.Phase.RELOAD) {
            return false;
        }
        if (reloadTicksRemaining > 0) {
            reloadTicksRemaining--;
        }
        return reloadTicksRemaining <= 0;
    }

    private boolean advanceAimTimer(BoomstickAttackState.Phase phase, boolean targetAvailable) {
        if (phase != BoomstickAttackState.Phase.AIM || !targetAvailable) {
            return false;
        }
        boolean complete = aimTicks >= AIM_WINDOW_TICKS;
        aimTicks++;
        return complete;
    }

    private boolean advanceCooldownTimer(BoomstickAttackState.Phase phase) {
        if (phase != BoomstickAttackState.Phase.COOLDOWN) {
            return false;
        }
        if (cooldownTicksRemaining > 0) {
            cooldownTicksRemaining--;
        }
        return cooldownTicksRemaining <= 0;
    }

    private void switchToSupportedWeapon() {
        if (!hasSupportedWeaponInInventory()) {
            return;
        }
        crossBowman.switchMainHandItem(SupportedBoomsticks::isSupportedWeapon);
        state.reset();
        activeWeapon = null;
        switchDelay = 1;
    }

    private void abortForWeaponChange(ItemStack previousWeapon) {
        stopReloadAnimation(previousWeapon);
        state.reset();
        shotOutcome = null;
        aimTicks = 0;
        reloadTicksRemaining = 0;
        cooldownTicksRemaining = 0;
    }

    private void stopReloadAnimation(ItemStack weapon) {
        adapter.setReloading(weapon, false);
        crossBowman.stopUsingItem();
    }

    private void clearWeaponAnimationState() {
        ItemStack weapon = crossBowman.getMainHandItem();
        if (adapter.supports(weapon)) {
            adapter.setReloading(weapon, false);
            adapter.setFiring(weapon, false);
        }
        crossBowman.stopUsingItem();
    }

    private void tickFireAnimation() {
        if (fireAnimationTicks <= 0) {
            return;
        }
        fireAnimationTicks--;
        if (fireAnimationTicks == 0) {
            ItemStack weapon = crossBowman.getMainHandItem();
            if (adapter.supports(weapon)) {
                adapter.setFiring(weapon, false);
            }
        }
    }

    private boolean isOperational() {
        return !crossBowman.level().isClientSide
                && crossBowman.isAlive()
                && CompatConfig.ENABLED.get()
                && crossBowman.getShouldRanged()
                && !crossBowman.getShouldRest()
                && !crossBowman.isPassenger();
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
        return adapter.supports(crossBowman.getMainHandItem()) || hasSupportedWeaponInInventory();
    }

    private boolean hasSupportedWeaponInInventory() {
        ItemStack matching = crossBowman.getMatchingItem(SupportedBoomsticks::isSupportedWeapon);
        return matching != null && !matching.isEmpty();
    }

    private boolean mainHandWeaponNeedsReload() {
        ItemStack weapon = crossBowman.getMainHandItem();
        return adapter.supports(weapon)
                && !adapter.isLoaded(weapon)
                && adapter.hasAmmo(crossBowman, weapon, BoomstickAmmoAccess.isAmmoRequired());
    }

    private AimPoint findAimPoint() {
        LivingEntity target = crossBowman.getTarget();
        if (validTarget(target) && crossBowman.hasLineOfSight(target)) {
            return new AimPoint(target.getEyePosition(1.0F), target);
        }
        if (hasStrategicFirePosition()) {
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

    private void moveAndLook(AimPoint aimPoint) {
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
                MAX_COMBAT_RANGE)) {
            crossBowman.getNavigation().moveTo(target, speedModifier);
        } else {
            crossBowman.getNavigation().stop();
        }
    }

    private record AimPoint(Vec3 position, LivingEntity entity) {
    }
}
