package org.iwoss.recruits_use_boomsticks.command;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickTransientStateRecovery;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.iwoss.recruits_use_boomsticks.inventory.RecruitHandSwap;
import org.iwoss.recruits_use_boomsticks.inventory.RecruitInventorySafety;
import org.iwoss.recruits_use_boomsticks.network.CarryFirearmCommandMessage;

import java.util.List;
import java.util.UUID;

/**
 * Server side of the command screen's carry order.
 *
 * <p>Recruits normally draw a firearm only when a combat goal starts. This order explicitly puts a
 * supported ranged weapon in the main hand, as its button promises. The reverse order moves it into
 * the shield hand and restores the recruit's sword, axe, or crossbow in the main hand, or leaves the
 * main hand empty when the recruit owns nothing else to hold. It walks the recruit's container
 * directly because Recruits' own {@code switchMainHandItem} skips both hand slots.</p>
 */
public final class BoomstickCarryOrder {
    /** Matches the radius Recruits' own group commands use. */
    private static final double COMMAND_RANGE = 100.0D;

    private static final String CARRY_TAG = RecruitsUseBoomsticks.MOD_ID + ":carrying_firearm";

    private BoomstickCarryOrder() {
    }

    /** Applies the order to every commanded recruit of the sending player. */
    public static void apply(ServerPlayer sender, List<UUID> groups, boolean draw) {
        if (sender == null || groups == null || groups.isEmpty() || !CompatConfig.ENABLED.get()) {
            return;
        }
        UUID owner = sender.getUUID();
        List<UUID> selectedGroups = groups.stream().distinct().toList();
        boolean everyone = selectedGroups.contains(CarryFirearmCommandMessage.EVERYONE);
        List<CrossBowmanEntity> commanded = sender.level()
                .getEntitiesOfClass(
                        CrossBowmanEntity.class,
                        sender.getBoundingBox().inflate(COMMAND_RANGE),
                        recruit -> everyone
                                ? recruit.isEffectedByCommand(owner, null)
                                : selectedGroups.stream()
                                .anyMatch(group -> recruit.isEffectedByCommand(owner, group)));
        int changed = 0;
        int armed = 0;
        int alreadyStood = 0;
        for (CrossBowmanEntity recruit : commanded) {
            if (ownsSupportedFirearm(recruit)) {
                armed++;
            }
            boolean wasInRequestedState = isInRequestedState(recruit, draw);
            if (wasInRequestedState) {
                alreadyStood++;
            }
            apply(recruit, draw);
            if (!wasInRequestedState && isInRequestedState(recruit, draw)) {
                changed++;
            }
        }
        // Without a report the order is invisible, and its three failure modes look identical in game:
        // nobody was commanded, nobody owns a firearm, or everyone already stood the way it asked.
        sender.sendSystemMessage(report(draw, changed, armed, commanded.size(), alreadyStood));
    }

    /** Names what the order actually did, including the reason it did nothing. */
    private static Component report(
            boolean draw,
            int changed,
            int armed,
            int commanded,
            int alreadyStood
    ) {
        if (commanded == 0) {
            return Component.translatable("chat.recruits_use_boomsticks.carry.nobody");
        }
        if (armed == 0) {
            return Component.translatable("chat.recruits_use_boomsticks.carry.unarmed", commanded);
        }
        // A stow order only fails now when the off hand is occupied and storage has no room for what
        // is in it, so nothing can move without dropping a stack. A formation that was already
        // standing the way the order asked has to fall through to the ordinary count instead.
        if (!draw && changed == 0 && alreadyStood == 0) {
            return Component.translatable("chat.recruits_use_boomsticks.carry.blocked", armed);
        }
        return Component.translatable(
                draw
                        ? "chat.recruits_use_boomsticks.weapons_out"
                        : "chat.recruits_use_boomsticks.weapons_away",
                changed,
                armed);
    }

    /** Whether the recruit owns a supported firearm in a hand or ordinary storage. */
    private static boolean ownsSupportedFirearm(CrossBowmanEntity recruit) {
        RecruitWeaponAdapters adapters = RecruitWeaponAdapters.production();
        if (adapters.isSupportedWeapon(recruit.getMainHandItem())) {
            return true;
        }
        SimpleContainer inventory = recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!RecruitHandSwap.isArmourSlot(recruit, slot)
                    && adapters.isSupportedWeapon(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether the recruit ends up carrying a supported firearm in its main hand. */
    public static boolean apply(CrossBowmanEntity recruit, boolean draw) {
        if (recruit == null || recruit.level().isClientSide) {
            return false;
        }
        // A firearm that is swapped out mid-reload would leave a borrowed tool in the off hand and a
        // marker no goal is driving, so any open loading chain is closed before the hands move.
        BoomstickTransientStateRecovery.recover(recruit);
        RecruitInventorySafety.repairInvalidArmour(recruit);

        boolean carrying = draw ? drawFirearm(recruit) : stowFirearm(recruit);
        setCarrying(recruit, carrying);
        if (CompatConfig.DEBUG_LOGGING.get()) {
            RecruitsUseBoomsticks.LOGGER.info(
                    "Carry order draw={} for recruit {}: main hand {}, off hand {}, carrying={}",
                    draw,
                    recruit.getId(),
                    recruit.getMainHandItem(),
                    recruit.getOffhandItem(),
                    carrying);
        }
        return carrying;
    }

    public static boolean isCarrying(CrossBowmanEntity recruit) {
        CompoundTag data = recruit.getPersistentData();
        return data.getBoolean(CARRY_TAG);
    }

    private static void setCarrying(CrossBowmanEntity recruit, boolean carrying) {
        CompoundTag data = recruit.getPersistentData();
        if (carrying) {
            data.putBoolean(CARRY_TAG, true);
        } else {
            data.remove(CARRY_TAG);
        }
    }

    private static boolean isInRequestedState(CrossBowmanEntity recruit, boolean draw) {
        RecruitWeaponAdapters adapters = RecruitWeaponAdapters.production();
        boolean firearmInMainHand = adapters.isSupportedEnabledWeapon(recruit.getMainHandItem());
        boolean firearmInOffHand = adapters.isSupportedWeapon(recruit.getOffhandItem());
        return draw ? firearmInMainHand : firearmInOffHand && !firearmInMainHand;
    }

    /**
     * Draws the ranged weapon into the main hand.
     *
     * <p>The displaced weapon is stored in the exact slot the firearm came from, so the reverse
     * order can put it back without duplicating or losing either stack.</p>
     */
    private static boolean drawFirearm(CrossBowmanEntity recruit) {
        RecruitWeaponAdapters adapters = RecruitWeaponAdapters.production();
        RecruitHandSwap.intoMainHand(recruit, adapters::isSupportedEnabledWeapon);
        return adapters.isSupportedEnabledWeapon(recruit.getMainHandItem());
    }

    private static boolean stowFirearm(CrossBowmanEntity recruit) {
        RecruitWeaponAdapters adapters = RecruitWeaponAdapters.production();
        // The config switches are deliberately ignored: a weapon carried while the integration was
        // still on has to be stowable after it was switched off.
        if (adapters.isSupportedWeapon(recruit.getMainHandItem())) {
            if (hasReplacementWeapon(recruit, adapters)
                    && RecruitHandSwap.rotateMainHandIntoOffHand(
                    recruit,
                    stack -> !adapters.isSupportedWeapon(stack)
                            && (isMeleeWeapon(stack) || stack.getItem() instanceof CrossbowItem))) {
                return false;
            }
            // Nothing to take instead is not a reason to keep the weapon raised: the order slings it
            // over the shoulder the way a player's off hand does and leaves the main hand empty. The
            // combat goal draws it back out of the off hand as soon as a fight starts.
            if (RecruitHandSwap.stowMainHandInOffHand(recruit, adapters::isSupportedWeapon)) {
                return false;
            }
        }
        RecruitHandSwap.intoOffHand(recruit, adapters::isSupportedWeapon);
        if (adapters.isSupportedWeapon(recruit.getOffhandItem())
                && (adapters.isSupportedWeapon(recruit.getMainHandItem())
                || recruit.getMainHandItem().isEmpty())) {
            takeBackOwnWeapon(recruit, adapters);
        }
        return adapters.isSupportedWeapon(recruit.getMainHandItem());
    }

    private static boolean hasReplacementWeapon(
            CrossBowmanEntity recruit,
            RecruitWeaponAdapters adapters
    ) {
        SimpleContainer inventory = recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (RecruitHandSwap.isArmourSlot(recruit, slot)) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!adapters.isSupportedWeapon(stack)
                    && (isMeleeWeapon(stack) || stack.getItem() instanceof CrossbowItem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the recruit's own weapon back in the main hand.
     *
     * <p>The melee test is Recruits' own: its melee goal equips a sword or an axe and nothing else,
     * and a crossbowman may also hold its crossbow.</p>
     */
    private static void takeBackOwnWeapon(CrossBowmanEntity recruit, RecruitWeaponAdapters adapters) {
        if (!RecruitHandSwap.intoMainHand(
                recruit,
                stack -> isMeleeWeapon(stack) && !adapters.isSupportedWeapon(stack))) {
            // Supported weapons are excluded explicitly: Medieval Boomsticks' own guns extend
            // CrossbowItem, so without this the weapon being stowed answers its own fallback and the
            // recruit keeps holding it.
            RecruitHandSwap.intoMainHand(
                    recruit,
                    stack -> stack.getItem() instanceof CrossbowItem && !adapters.isSupportedWeapon(stack));
        }
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
}
