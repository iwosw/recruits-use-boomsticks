package org.iwoss.recruits_use_boomsticks.compat;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Server-safe Artillery Addon boundary. It uses registry identities and the native projectile
 * registry, never the addon's player-only item/procedure/network entry points.
 */
public final class ArtilleryAddonAdapter implements BoomstickWeaponAdapter {
    public static final ArtilleryAddonAdapter INSTANCE = new ArtilleryAddonAdapter(
            () -> ModList.get().isLoaded(SupportedArtillery.MOD_ID));

    private static final String IRONBALL_PROJECTILE_CLASS =
            "net.mcreator.artilleryaddon.entity.IronballProjectileEntity";
    private static final String ARTILLERY_FIRE_SOUND = SupportedArtillery.MOD_ID + ":arquebus_firing";
    private static final String ARTILLERY_HAND_CANNON_FIRE_SOUND =
            SupportedArtillery.MOD_ID + ":hand_cannon_fireing";
    private static final String SAVED_OFFHAND_KEY =
            "recruits_use_boomsticks:artillery_saved_offhand";
    private static final String BORROWED_COMPONENT_KEY =
            "recruits_use_boomsticks:artillery_borrowed_component";
    public static final String COMPATIBILITY_MARKER_KEY =
            "recruits_use_boomsticks:artillery_arrow_projectile";

    private final BooleanSupplier availability;

    public ArtilleryAddonAdapter(BooleanSupplier availability) {
        this.availability = java.util.Objects.requireNonNull(availability, "availability");
    }

    public boolean isAvailable() {
        try {
            return availability.getAsBoolean();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public RecruitWeaponIntegration integration() {
        return RecruitWeaponIntegration.ARTILLERY_ADDON;
    }

    @Override
    public boolean supports(ItemStack weapon) {
        return isAvailable() && profile(weapon).isPresent();
    }

    @Override
    public boolean supportsAmmo(ItemStack ammo) {
        if (!isAvailable()) {
            return false;
        }
        String ammoId = registryId(ammo);
        return SupportedArtillery.IRON_BALL_ID.equals(ammoId)
                || SupportedArtillery.SMALL_IRON_BALL_ID.equals(ammoId)
                || SupportedArtillery.LARGE_IRON_BALL_ID.equals(ammoId)
                || SupportedArtillery.VANILLA_ARROW_ID.equals(ammoId);
    }

    @Override
    public boolean supportsProjectile(Class<?> projectileType) {
        if (!isAvailable() || projectileType == null) {
            return false;
        }
        return hasNamedSuperclass(projectileType, IRONBALL_PROJECTILE_CLASS);
    }

    @Override
    public boolean supportsProjectile(AbstractArrow projectile) {
        return isAvailable()
                && projectile != null
                && (supportsProjectile(projectile.getClass())
                || projectile.getPersistentData().getBoolean(COMPATIBILITY_MARKER_KEY));
    }

    public boolean supportsProjectileClassName(String className) {
        return isAvailable() && IRONBALL_PROJECTILE_CLASS.equals(className);
    }

    @Override
    public Optional<BoomstickWeaponProfile> profile(ItemStack weapon) {
        String weaponId = registryId(weapon);
        if (!isAvailable() || !SupportedArtillery.isGameplayWeapon(weaponId)) {
            return Optional.empty();
        }
        return SupportedArtillery.profileFor(weaponId)
                .map(ArtilleryWeaponProfile::toBoomstickProfile);
    }

    private Optional<ArtilleryWeaponProfile> artilleryProfile(ItemStack weapon) {
        String weaponId = registryId(weapon);
        if (!isAvailable() || !SupportedArtillery.isGameplayWeapon(weaponId)) {
            return Optional.empty();
        }
        return SupportedArtillery.profileFor(weaponId);
    }

    @Override
    public boolean isLoaded(ItemStack weapon) {
        return artilleryProfile(weapon)
                .map(profile -> ArtilleryNativeState.isLoaded(weapon, profile))
                .orElse(false);
    }

    @Override
    public void setLoaded(ItemStack weapon, boolean loaded) {
        artilleryProfile(weapon).ifPresent(profile -> {
            if (loaded) {
                ArtilleryNativeState.markLoaded(weapon, profile);
            } else {
                ArtilleryNativeState.markFired(weapon, profile);
            }
        });
    }

    @Override
    public void setReloading(ItemStack weapon, boolean reloading) {
        if (artilleryProfile(weapon).isPresent()) {
            ArtilleryNativeState.setReloading(weapon, reloading);
        }
    }

    @Override
    public boolean isReloading(ItemStack weapon) {
        return artilleryProfile(weapon).isPresent() && ArtilleryNativeState.isReloading(weapon);
    }

    @Override
    public void setFiring(ItemStack weapon, boolean firing) {
        if (artilleryProfile(weapon).isPresent()) {
            ArtilleryNativeState.setFiring(weapon, firing);
        }
    }

    @Override
    public int reloadStepCount(ItemStack weapon) {
        return artilleryProfile(weapon)
                .map(profile -> ArtilleryReloadProtocol.stepsFor(profile.registryId()).size())
                .orElse(0);
    }

    @Override
    public boolean hasReloadComponents(CrossBowmanEntity recruit, ItemStack weapon) {
        if (recruit == null) {
            return false;
        }
        Optional<ArtilleryWeaponProfile> profileResult = artilleryProfile(weapon);
        if (profileResult.isEmpty()) {
            return false;
        }
        List<ArtilleryReloadStep> steps =
                ArtilleryReloadProtocol.stepsFor(profileResult.orElseThrow().registryId());
        if (steps.isEmpty()) {
            return true;
        }
        // A chain that spends the same component more than once needs every unit up front: a
        // per-step presence check would start a Chu Ko Nu magazine on a single arrow and abandon it
        // halfway with the earlier rounds already gone.
        return ArtilleryComponentAccess.satisfiesAll(recruit.getInventory(), steps);
    }

    @Override
    public boolean applyReloadStep(CrossBowmanEntity recruit, ItemStack weapon, int stepIndex) {
        if (recruit == null || !(recruit.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Optional<ArtilleryWeaponProfile> profileResult = artilleryProfile(weapon);
        if (profileResult.isEmpty()) {
            return false;
        }
        ArtilleryWeaponProfile profile = profileResult.orElseThrow();
        List<ArtilleryReloadStep> steps = ArtilleryReloadProtocol.stepsFor(profile.registryId());
        if (stepIndex < 0 || stepIndex >= steps.size()) {
            return false;
        }
        ArtilleryReloadStep step = steps.get(stepIndex);
        ArtilleryReloadStep.ComponentRequirement component =
                ArtilleryComponentAccess.select(recruit.getInventory(), step);
        if (component == null) {
            return false;
        }

        try {
            // The native procedure holds the tool in the hand that is free; a recruit must keep the
            // weapon in its main hand, so the mirrored tool is shown in the off hand instead.
            // A recruit's equipment slots are backed by its inventory, so the real stack is moved
            // rather than copied — a display copy would duplicate the item.
            // A leftover borrow is returned before the off hand is snapshotted, so a transaction
            // that ended abnormally cannot make this one save a borrowed tool as the real off hand
            // and then hand out a second copy of it when the snapshot is restored.
            returnBorrowedComponent(recruit);
            saveOffhandOnce(recruit, steps);
            // A native bare-hand branch borrows nothing and pays nothing.
            if (!component.isEmptyHand()) {
                if (!borrowComponent(recruit, component, step.componentUse())) {
                    return false;
                }
                ArtilleryComponentAccess.payStepCost(
                        recruit.getInventory().getItem(
                                recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND)),
                        step.componentUse(),
                        recruit.getRandom());
            }
            ArtilleryNativeState.applyReloadStep(weapon, profile, step);
            recruit.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            playStepSound(serverLevel, recruit, step.sound());
            if (CompatConfig.DEBUG_LOGGING.get()) {
                RecruitsUseBoomsticks.LOGGER.info(
                        "Artillery reload step {}/{} for {} on recruit {}: component={} offhand={} stage={}",
                        stepIndex + 1,
                        steps.size(),
                        profile.registryId(),
                        recruit.getId(),
                        component.id(),
                        recruit.getOffhandItem(),
                        weapon.getOrCreateTag().getDouble(ArtilleryNativeState.STAGE_KEY));
            }
            return true;
        } catch (RuntimeException | LinkageError exception) {
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Artillery reload step {} failed for recruit {} with weapon {}",
                    stepIndex,
                    recruit.getId(),
                    profile.registryId(),
                    exception);
            return false;
        }
    }

    @Override
    public void endSteppedReload(CrossBowmanEntity recruit, ItemStack weapon) {
        if (recruit == null) {
            return;
        }
        // The off hand is restored from the recruit's own transaction keys, so it is repaired even
        // when the recruit no longer holds the weapon that borrowed the component.
        restoreOffhand(recruit);
        // Only this adapter's own transient lore may be removed. Recovery calls every adapter for
        // whatever the recruit happens to hold, and an unrelated item's display lore is not ours.
        if (artilleryProfile(weapon).isPresent()) {
            ArtilleryNativeState.clearNativeLore(weapon);
        }
    }

    /**
     * Puts the match in the recruit's free hand for the shot.
     *
     * <p>Natively a player holds the match in one hand and the loaded gun in the other; a recruit
     * keeps the weapon in its main hand, so the match is mirrored into the off hand exactly as the
     * loading chain mirrors a flask or a ramrod. Nothing is spent: the native branch only checks the
     * match, and a recruit that owns none simply fires without showing one.</p>
     */
    @Override
    public void showFiringTool(CrossBowmanEntity recruit, ItemStack weapon) {
        if (recruit == null || recruit.level().isClientSide) {
            return;
        }
        Optional<ArtilleryWeaponProfile> profile = artilleryProfile(weapon);
        if (profile.isEmpty() || !ArtilleryReloadProtocol.firesWithMatch(profile.get().registryId())) {
            return;
        }
        try {
            returnBorrowedComponent(recruit);
            saveOffhandOnce(recruit, List.of());
            borrowComponent(
                    recruit,
                    new ArtilleryReloadStep.ComponentRequirement(
                            ArtilleryReloadProtocol.MATCH_ID,
                            ArtilleryReloadStep.ComponentRequirement.Kind.ITEM),
                    ArtilleryReloadStep.ComponentUse.NONE);
        } catch (RuntimeException | LinkageError exception) {
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Artillery match display failed for recruit {}",
                    recruit.getId(),
                    exception);
        }
    }

    @Override
    public void clearFiringTool(CrossBowmanEntity recruit) {
        if (recruit == null || recruit.level().isClientSide) {
            return;
        }
        restoreOffhand(recruit);
    }

    /**
     * Moves the real component into the visible off hand.
     *
     * <p>A recruit's off hand is backed by an inventory slot, so the inventory is the single source
     * of truth here; writing a copy through the equipment slot alone would duplicate the item.</p>
     *
     * <p>A consumed component is split off one at a time, so a stack of ammunition is never shuttled
     * in and out of the off hand as a whole. A tool keeps its identity and durability, so the real
     * stack is moved instead.</p>
     */
    private static boolean borrowComponent(
            CrossBowmanEntity recruit,
            ArtilleryReloadStep.ComponentRequirement component,
            ArtilleryReloadStep.ComponentUse componentUse
    ) {
        Container inventory = recruit.getInventory();
        int offhandSlot = recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND);
        int slot = findComponentSlotOutsideOffhand(inventory, component, offhandSlot);
        if (slot < 0) {
            return false;
        }
        ItemStack source = inventory.getItem(slot);
        ItemStack borrowed;
        if (componentUse == ArtilleryReloadStep.ComponentUse.CONSUME_ONE && source.getCount() > 1) {
            borrowed = source.split(1);
            inventory.setItem(slot, source);
        } else {
            borrowed = source;
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        inventory.setItem(offhandSlot, borrowed);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, borrowed);
        recruit.getPersistentData().putBoolean(BORROWED_COMPONENT_KEY, true);
        inventory.setChanged();
        return true;
    }

    /** Returns whatever survived the step to the inventory and frees the off hand again. */
    private static void returnBorrowedComponent(CrossBowmanEntity recruit) {
        CompoundTag data = recruit.getPersistentData();
        if (!data.getBoolean(BORROWED_COMPONENT_KEY)) {
            return;
        }
        Container inventory = recruit.getInventory();
        int offhandSlot = recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND);
        ItemStack borrowed = inventory.getItem(offhandSlot);
        inventory.setItem(offhandSlot, ItemStack.EMPTY);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        data.remove(BORROWED_COMPONENT_KEY);
        giveBack(recruit, borrowed);
        inventory.setChanged();
    }

    /**
     * Puts a stack back into the recruit's inventory, or on the ground when it no longer fits.
     *
     * <p>The off-hand slot is deliberately skipped. It belongs to the same container, so a plain
     * {@code addItem} happily puts a returned powder flask straight back into the hand this method
     * was called to free — and the next chain then snapshots that flask as the recruit's own
     * off-hand item and faithfully restores it when the chain finishes. A weapon whose native branch
     * requires an empty or fork-rest off hand is silently unable to fire from then on.</p>
     *
     * <p>A recruit whose inventory filled up meanwhile must still not swallow the item.</p>
     */
    private static void giveBack(CrossBowmanEntity recruit, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Container inventory = recruit.getInventory();
        int offhandSlot = recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND);
        for (int slot = 0; slot < inventory.getContainerSize() && !stack.isEmpty(); slot++) {
            if (slot == offhandSlot) {
                continue;
            }
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty()) {
                inventory.setItem(slot, stack.split(stack.getCount()));
            } else if (ItemStack.isSameItemSameTags(candidate, stack)) {
                int room = Math.min(candidate.getMaxStackSize(), inventory.getMaxStackSize())
                        - candidate.getCount();
                if (room > 0) {
                    candidate.grow(stack.split(Math.min(room, stack.getCount())).getCount());
                    inventory.setItem(slot, candidate);
                }
            }
        }
        if (!stack.isEmpty()) {
            recruit.spawnAtLocation(stack);
        }
    }

    private static int findComponentSlotOutsideOffhand(
            Container inventory,
            ArtilleryReloadStep.ComponentRequirement component,
            int offhandSlot
    ) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot != offhandSlot
                    && ArtilleryComponentAccess.matches(inventory.getItem(slot), component)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Snapshots the recruit's own off-hand item once per loading transaction.
     *
     * <p>An item this very chain loads with is stowed instead of snapshotted. A powder flask that is
     * already sitting in the off hand — left there by an earlier transaction, or handed over by a
     * player — would otherwise be recorded as the recruit's own equipment and put straight back at
     * the end of every chain. The chain also could not borrow it, because a borrow only ever moves a
     * component out of a slot other than the off hand, so the recruit would abort at its first step
     * and stay stuck holding a flask it can neither use nor put away.</p>
     */
    private static void saveOffhandOnce(CrossBowmanEntity recruit, List<ArtilleryReloadStep> steps) {
        CompoundTag data = recruit.getPersistentData();
        if (data.contains(SAVED_OFFHAND_KEY)) {
            return;
        }
        Container inventory = recruit.getInventory();
        int offhandSlot = recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND);
        ItemStack occupant = inventory.getItem(offhandSlot);
        if (isChainComponent(occupant, steps)) {
            inventory.setItem(offhandSlot, ItemStack.EMPTY);
            recruit.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            giveBack(recruit, occupant);
            inventory.setChanged();
            occupant = ItemStack.EMPTY;
        }
        data.put(SAVED_OFFHAND_KEY, occupant.save(new CompoundTag()));
    }

    /** Whether the stack is something this chain loads with rather than the recruit's own gear. */
    private static boolean isChainComponent(ItemStack stack, List<ArtilleryReloadStep> steps) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (ArtilleryReloadStep step : steps) {
            for (ArtilleryReloadStep.ComponentRequirement component : step.components()) {
                if (!component.isEmptyHand() && ArtilleryComponentAccess.matches(stack, component)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Puts the recruit's own off-hand item back and drops the transaction keys.
     *
     * <p>This is the only place that owns those keys, so it is safe to call from recovery, from the
     * end of a chain, and from the recruit's death — whichever happens first wins and the rest
     * become no-ops.</p>
     */
    static void restoreOffhand(CrossBowmanEntity recruit) {
        returnBorrowedComponent(recruit);
        CompoundTag data = recruit.getPersistentData();
        if (!data.contains(SAVED_OFFHAND_KEY)) {
            return;
        }
        ItemStack saved = ItemStack.of(data.getCompound(SAVED_OFFHAND_KEY));
        data.remove(SAVED_OFFHAND_KEY);
        if (saved.isEmpty()) {
            // The off hand was empty when the chain started, so there is nothing to put back and
            // anything the recruit has picked up since stays where it is.
            return;
        }
        Container inventory = recruit.getInventory();
        int offhandSlot = recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND);
        // Anything that arrived in the off hand while the chain was running is kept: a player may
        // have equipped the recruit through its screen, and the snapshot must not delete that. The
        // snapshot is written back first so the give-back cannot reuse the slot it just freed.
        ItemStack occupant = inventory.getItem(offhandSlot);
        inventory.setItem(offhandSlot, saved);
        recruit.setItemSlot(EquipmentSlot.OFFHAND, saved);
        giveBack(recruit, occupant);
        inventory.setChanged();
    }

    private static void playStepSound(
            ServerLevel level,
            CrossBowmanEntity recruit,
            BoomstickSound sound
    ) {
        if (sound == BoomstickSound.NONE) {
            return;
        }
        try {
            SoundEvent event = soundFor(sound);
            if (event == null) {
                return;
            }
            level.playSound(
                    null,
                    recruit.getX(),
                    recruit.getY(),
                    recruit.getZ(),
                    event,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        } catch (RuntimeException exception) {
            RecruitsUseBoomsticks.LOGGER.warn("Artillery reload step sound failed", exception);
        }
    }

    @Override
    public int reloadTicks(ItemStack weapon) {
        return artilleryProfile(weapon).map(ArtilleryWeaponProfile::reloadTicks).orElse(0);
    }

    @Override
    public int cooldownTicks(ItemStack weapon) {
        return artilleryProfile(weapon).map(ArtilleryWeaponProfile::cooldownTicks).orElse(0);
    }

    @Override
    public boolean hasAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        if (recruit == null) {
            return false;
        }
        // The shared flag describes host/vanilla ammo policy; Artillery's iron ball is always physical ammo.
        // A repeater commits its whole magazine in one transaction, so a partial magazine is refused.
        // A weapon with a captured native chain also needs every loading tool before it may start.
        return artilleryProfile(weapon)
                .map(profile -> ArtilleryAmmoAccess.count(recruit.getInventory(), profile.ammoId())
                        >= requiredAmmo(profile)
                        && hasReloadComponents(recruit, weapon))
                .orElse(false);
    }

    @Override
    public boolean consumeAmmo(CrossBowmanEntity recruit, ItemStack weapon, boolean ammoRequired) {
        if (recruit == null) {
            return false;
        }
        // Keep the Artillery transaction independent from Recruits' optional vanilla-arrow setting.
        return artilleryProfile(weapon)
                .map(profile -> ArtilleryAmmoAccess.consume(
                        recruit.getInventory(),
                        profile.ammoId(),
                        requiredAmmo(profile)))
                .orElse(false);
    }

    /**
     * Ammunition one reload needs.
     *
     * <p>A weapon with a captured native chain spends exactly what that chain's own consuming steps
     * spend. Only a weapon without a captured chain falls back to the profile's policy value.</p>
     */
    private static int requiredAmmo(ArtilleryWeaponProfile profile) {
        return ArtilleryReloadProtocol.hasSteppedChain(profile.registryId())
                ? ArtilleryReloadProtocol.ammoConsumed(profile.registryId())
                : profile.ammoPerReload();
    }

    @Override
    public ShotResult fire(CrossBowmanEntity recruit, ItemStack weapon, Vec3 targetPosition) {
        Optional<ArtilleryWeaponProfile> profileResult = artilleryProfile(weapon);
        if (profileResult.isEmpty()) {
            return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
        }
        if (recruit == null || !recruit.isAlive()) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }
        if (!(recruit.level() instanceof ServerLevel serverLevel)) {
            return new ShotResult(
                    recruit.level().isClientSide ? ShotOutcome.CLIENT_SIDE_REJECTED : ShotOutcome.INVALID_TARGET,
                    0);
        }
        if (targetPosition == null || !isFinite(targetPosition)) {
            return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
        }

        ArtilleryWeaponProfile profile = profileResult.orElseThrow();
        if (!ArtilleryNativeState.isLoaded(weapon, profile)) {
            return new ShotResult(ShotOutcome.NOT_LOADED, 0);
        }
        if (!supportsShooterBranch(recruit, profile)) {
            // The native procedure exits before creating a projectile when the off hand holds
            // anything other than a fork rest, and that gate is preserved rather than bypassed. It
            // is otherwise invisible, so a recruit that will never fire says why at least once.
            if (CompatConfig.DEBUG_LOGGING.get()) {
                RecruitsUseBoomsticks.LOGGER.debug(
                        "Artillery {} on recruit {} cannot fire: its native branch needs an empty or"
                                + " {} off hand but holds {}",
                        profile.registryId(),
                        recruit.getId(),
                        SupportedArtillery.FORK_REST_ID,
                        recruit.getOffhandItem());
            }
            return new ShotResult(ShotOutcome.INVALID_WEAPON, 0);
        }

        ItemStack originalWeapon = weapon.copy();
        List<AbstractArrow> projectiles = new ArrayList<>(profile.projectileCount());
        AbstractArrow currentProjectile = null;
        try {
            ArtilleryWeaponProfile.NativeMisfirePolicy misfirePolicy = profile.nativeMisfirePolicy();
            if (misfirePolicy.enabled()
                    && misfirePolicy.misfires(
                    Mth.nextDouble(
                            recruit.getRandom(),
                            misfirePolicy.randomMinimum(),
                            misfirePolicy.randomMaximum()),
                    weapon.getDamageValue())) {
                ArtilleryNativeState.markFired(weapon, profile);
                return new ShotResult(ShotOutcome.MISFIRED, 0);
            }

            Vec3 origin = new Vec3(recruit.getX(), recruit.getEyeY() - 0.1D, recruit.getZ());
            Vec3 direction = aimVector(origin, targetPosition, profile.projectileVelocity());
            if (direction.lengthSqr() < 1.0E-8D) {
                return new ShotResult(ShotOutcome.INVALID_TARGET, 0);
            }

            boolean useAlternateInaccuracy = switch (profile.alternateInaccuracyBranch()) {
                case NONE -> false;
                case SHIFT -> recruit.isShiftKeyDown();
                case PASSENGER -> recruit.isPassenger();
                case FORK_REST -> isForkRestEquipped(recruit);
            };
            float inaccuracy = useAlternateInaccuracy
                    ? profile.alternateInaccuracy()
                    : profile.inaccuracy();
            for (int index = 0; index < profile.projectileCount(); index++) {
                currentProjectile = createProjectile(serverLevel, profile);
                if (currentProjectile == null) {
                    discardProjectiles(projectiles);
                    restoreStack(weapon, originalWeapon);
                    return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
                }
                configureProjectile(currentProjectile, profile, recruit, origin);
                currentProjectile.shoot(
                        direction.x,
                        direction.y,
                        direction.z,
                        (float) profile.projectileVelocity(),
                        inaccuracy);
                if (!serverLevel.addFreshEntity(currentProjectile)) {
                    currentProjectile.remove(Entity.RemovalReason.DISCARDED);
                    currentProjectile = null;
                    discardProjectiles(projectiles);
                    restoreStack(weapon, originalWeapon);
                    return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
                }
                projectiles.add(currentProjectile);
                currentProjectile = null;
            }

            if (CompatConfig.DEBUG_LOGGING.get()) {
                RecruitsUseBoomsticks.LOGGER.info(
                        "Artillery shot from {} at velocity {}: origin={} target={} aim={}",
                        profile.registryId(),
                        profile.projectileVelocity(),
                        origin,
                        targetPosition,
                        direction);
            }
            weapon.hurtAndBreak(1, recruit,
                    ignored -> recruit.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
            ArtilleryNativeState.markFired(weapon, profile);
            ArtilleryNativeState.setFiring(weapon, true);
            playShotEffectsSafely(serverLevel, recruit, profile, origin);
            return new ShotResult(ShotOutcome.FIRED, projectiles.size());
        } catch (RuntimeException | LinkageError exception) {
            if (currentProjectile != null) {
                currentProjectile.remove(Entity.RemovalReason.DISCARDED);
            }
            discardProjectiles(projectiles);
            restoreStack(weapon, originalWeapon);
            RecruitsUseBoomsticks.LOGGER.warn(
                    "Artillery shot failed for recruit {} with weapon {}",
                    recruit.getId(),
                    profile.registryId(),
                    exception);
            return new ShotResult(ShotOutcome.SPAWN_FAILED, 0);
        }
    }

    /** Per-tick gravity {@link AbstractArrow} applies to its own motion. */
    private static final double ARROW_GRAVITY_PER_TICK = 0.05D;
    /** Longest lead this policy will add, so a hopeless long shot cannot aim at the sky. */
    private static final double MAX_AIM_ARC = 8.0D;

    /**
     * Builds the launch vector, adding the upward arc a projectile needs to reach its target.
     *
     * <p>Recruits aim at a third of the target's height, below their own eyes, and every projectile
     * here extends {@link AbstractArrow} and therefore falls at {@value #ARROW_GRAVITY_PER_TICK}
     * blocks per tick squared. Without a lead the shot lands visibly short and low.</p>
     *
     * <p>{@code shoot} treats velocity as blocks per tick, so flight time is roughly the horizontal
     * distance divided by that velocity, and the drop over that flight is {@code g/2 * t^2}. Adding
     * exactly that drop back is self-calibrating: a fast iron ball barely arcs while a slow bolt
     * gets a real lob. Drag makes the true flight slightly longer, so this stays a mild
     * under-compensation rather than an overshoot.</p>
     */
    static Vec3 aimVector(Vec3 origin, Vec3 target, double projectileVelocity) {
        double dx = target.x - origin.x;
        double dy = target.y - origin.y;
        double dz = target.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= 0.0D || !Double.isFinite(projectileVelocity) || projectileVelocity <= 0.0D) {
            return new Vec3(dx, dy, dz);
        }
        double flightTicks = horizontal / projectileVelocity;
        double arc = Math.min(MAX_AIM_ARC, 0.5D * ARROW_GRAVITY_PER_TICK * flightTicks * flightTicks);
        return new Vec3(dx, dy + arc, dz);
    }

    /**
     * Horizontal distance past which {@link #aimVector} can no longer pay for the drop.
     *
     * <p>This is the inverse of the arc above at its cap: the compensation grows with the square of
     * the flight time until it hits {@link #MAX_AIM_ARC}, and beyond that point the lead is clipped
     * and the projectile lands short no matter how long the recruit aims. A slow projectile reaches
     * that wall early — a thrown cobblestone at velocity {@code 0.75} runs out at roughly thirteen
     * blocks — so the combat goal uses this to decide when to close the distance instead of lobbing
     * shots into the ground.</p>
     */
    public static double maxCompensatedRange(double projectileVelocity) {
        if (!Double.isFinite(projectileVelocity) || projectileVelocity <= 0.0D) {
            return 0.0D;
        }
        return projectileVelocity * Math.sqrt(2.0D * MAX_AIM_ARC / ARROW_GRAVITY_PER_TICK);
    }

    private static AbstractArrow createProjectile(ServerLevel level, ArtilleryWeaponProfile profile) {
        if (SupportedArtillery.VANILLA_ARROW_ID.equals(profile.projectileEntityId())) {
            return new Arrow(level, 0.0D, 0.0D, 0.0D);
        }

        ResourceLocation projectileId = ResourceLocation.tryParse(profile.projectileEntityId());
        EntityType<?> entityType = projectileId == null
                ? null
                : ForgeRegistries.ENTITY_TYPES.getValue(projectileId);
        if (entityType == null) {
            return null;
        }

        Entity created = entityType.create(level);
        if (!(created instanceof AbstractArrow arrow)
                || !hasNamedSuperclass(created.getClass(), IRONBALL_PROJECTILE_CLASS)) {
            if (created != null) {
                created.remove(Entity.RemovalReason.DISCARDED);
            }
            return null;
        }
        return arrow;
    }

    private static void configureProjectile(
            AbstractArrow projectile,
            ArtilleryWeaponProfile profile,
            CrossBowmanEntity recruit,
            Vec3 origin
    ) {
        projectile.setOwner(recruit);
        projectile.pickup = profile.pickupAllowed()
                ? AbstractArrow.Pickup.ALLOWED
                : AbstractArrow.Pickup.DISALLOWED;
        projectile.setBaseDamage(profile.baseDamage());
        projectile.setKnockback(1);
        projectile.setPierceLevel((byte) profile.pierceLevel());
        projectile.setSilent(!SupportedArtillery.VANILLA_ARROW_ID.equals(profile.projectileEntityId())
                && profile.silent());
        projectile.setSecondsOnFire(0);
        projectile.setCritArrow(profile.critical());
        if (SupportedArtillery.VANILLA_ARROW_ID.equals(profile.projectileEntityId())) {
            projectile.getPersistentData().putBoolean(COMPATIBILITY_MARKER_KEY, true);
        }
        projectile.setPos(origin.x, origin.y, origin.z);
    }

    private static void discardProjectiles(List<AbstractArrow> projectiles) {
        for (AbstractArrow projectile : projectiles) {
            projectile.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    private static void playShotEffectsSafely(
            ServerLevel level,
            CrossBowmanEntity recruit,
            ArtilleryWeaponProfile profile,
            Vec3 origin
    ) {
        try {
            SoundEvent sound = soundFor(profile.firingSound());
            level.playSound(
                    null,
                    recruit.getX(),
                    recruit.getY(),
                    recruit.getZ(),
                    sound == null ? SoundEvents.CROSSBOW_SHOOT : sound,
                    SoundSource.PLAYERS,
                    5.0F,
                    1.0F);
            if (CompatConfig.SMOKE_PARTICLES.get()) {
                level.sendParticles(
                        ParticleTypes.SMOKE,
                        origin.x,
                        origin.y,
                        origin.z,
                        5,
                        0.08D,
                        0.08D,
                        0.08D,
                        0.02D);
            }
        } catch (RuntimeException exception) {
            RecruitsUseBoomsticks.LOGGER.warn("Artillery shot effects failed", exception);
        }
    }

    private static SoundEvent soundFor(BoomstickSound sound) {
        if (sound == BoomstickSound.ARTILLERY_FIRE) {
            ResourceLocation soundId = ResourceLocation.tryParse(ARTILLERY_FIRE_SOUND);
            SoundEvent artillerySound = soundId == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(soundId);
            return artillerySound == null ? SoundEvents.CROSSBOW_SHOOT : artillerySound;
        }
        if (sound == BoomstickSound.ARTILLERY_HAND_CANNON_FIRE) {
            ResourceLocation soundId = ResourceLocation.tryParse(ARTILLERY_HAND_CANNON_FIRE_SOUND);
            SoundEvent artillerySound = soundId == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(soundId);
            return artillerySound == null ? SoundEvents.CROSSBOW_SHOOT : artillerySound;
        }
        String stepSoundId = stepSoundId(sound);
        if (stepSoundId != null) {
            // A loading step stays silent rather than borrowing an unrelated vanilla sound.
            ResourceLocation soundId = ResourceLocation.tryParse(stepSoundId);
            return soundId == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        }
        return SoundEvents.CROSSBOW_SHOOT;
    }

    /** Native loading-step sound identities confirmed in the addon's `sounds.json`. */
    private static String stepSoundId(BoomstickSound sound) {
        return switch (sound) {
            case ARTILLERY_LOADING_POWDER -> SupportedArtillery.MOD_ID + ":hand_cannon_loading_powder";
            case ARTILLERY_LOAD_BALL -> SupportedArtillery.MOD_ID + ":arquebus_ball";
            case ARTILLERY_RAMMING -> SupportedArtillery.MOD_ID + ":arquebus_ramming";
            case ARTILLERY_HAND_CANNON_LOAD_BALL -> SupportedArtillery.MOD_ID + ":hand_cannon_load_ball";
            case ARTILLERY_HAND_CANNON_RAMMING -> SupportedArtillery.MOD_ID + ":hand_cannon_ramming";
            case CROSSBOW_LOADING_START -> "minecraft:item.crossbow.loading_start";
            case CROSSBOW_LOADING_MIDDLE -> "minecraft:item.crossbow.loading_middle";
            case CROSSBOW_LOADING_END -> "minecraft:item.crossbow.loading_end";
            default -> null;
        };
    }

    private static void restoreStack(ItemStack target, ItemStack snapshot) {
        target.setCount(snapshot.getCount());
        target.setDamageValue(snapshot.getDamageValue());
        target.setTag(snapshot.hasTag() ? snapshot.getTag().copy() : null);
    }

    private static String registryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static boolean supportsShooterBranch(
            CrossBowmanEntity recruit,
            ArtilleryWeaponProfile profile
    ) {
        if (profile.alternateInaccuracyBranch() != ArtilleryWeaponProfile.InaccuracyBranch.FORK_REST) {
            return true;
        }
        ItemStack offhand = recruit.getOffhandItem();
        return offhand.isEmpty() || isForkRestEquipped(recruit);
    }

    private static boolean isForkRestEquipped(CrossBowmanEntity recruit) {
        return SupportedArtillery.FORK_REST_ID.equals(registryId(recruit.getOffhandItem()));
    }

    private static boolean hasNamedSuperclass(Class<?> type, String expectedName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (expectedName.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
