package org.iwoss.recruits_use_boomsticks.command;

import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.network.PacketDistributor;
import org.iwoss.recruits_use_boomsticks.RecruitsUseBoomsticks;
import org.iwoss.recruits_use_boomsticks.compat.BoomstickTransientStateRecovery;
import org.iwoss.recruits_use_boomsticks.compat.RecruitWeaponAdapters;
import org.iwoss.recruits_use_boomsticks.config.CompatConfig;
import org.iwoss.recruits_use_boomsticks.network.BoomstickNetwork;
import org.iwoss.recruits_use_boomsticks.network.CarryFirearmStateMessage;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server side of the command screen's carry order.
 *
 * <p>Recruits only draw a firearm when a combat goal starts, so an idle company keeps its melee
 * weapon in hand no matter what it carries. This order moves a supported firearm into the main hand
 * on demand and puts it back again, using Recruits' own {@code switchMainHandItem} so equipment and
 * inventory slots stay in the single place that owns them.</p>
 */
public final class BoomstickCarryOrder {
    /** Matches the radius Recruits' own group commands use. */
    private static final double COMMAND_RANGE = 100.0D;

    private static final String CARRY_TAG = RecruitsUseBoomsticks.MOD_ID + ":carrying_firearm";

    private BoomstickCarryOrder() {
    }

    /** Applies the order to every commanded recruit of the sending player. */
    public static void apply(ServerPlayer sender, UUID group, boolean draw) {
        if (sender == null || !CompatConfig.ENABLED.get()) {
            return;
        }
        UUID owner = sender.getUUID();
        List<CrossBowmanEntity> commanded = sender.level()
                .getEntitiesOfClass(
                        CrossBowmanEntity.class,
                        sender.getBoundingBox().inflate(COMMAND_RANGE),
                        recruit -> recruit.isEffectedByCommand(owner, group));
        int changed = 0;
        int armed = 0;
        for (CrossBowmanEntity recruit : commanded) {
            if (ownsSupportedFirearm(recruit)) {
                armed++;
            }
            boolean wasCarrying = isCarrying(recruit);
            if (apply(recruit, draw) != wasCarrying) {
                changed++;
            }
        }
        // Without a report the order is invisible, and its three failure modes look identical in game:
        // nobody was commanded, nobody owns a firearm, or everyone already stood the way it asked.
        sender.sendSystemMessage(report(draw, changed, armed, commanded.size()));
    }

    /** Names what the order actually did, including the reason it did nothing. */
    private static Component report(boolean draw, int changed, int armed, int commanded) {
        if (commanded == 0) {
            return Component.translatable("chat.recruits_use_boomsticks.carry.nobody");
        }
        if (armed == 0) {
            return Component.translatable("chat.recruits_use_boomsticks.carry.unarmed", commanded);
        }
        if (!draw && changed == 0) {
            return Component.translatable("chat.recruits_use_boomsticks.carry.no_replacement", armed);
        }
        return Component.translatable(
                draw
                        ? "chat.recruits_use_boomsticks.weapons_out"
                        : "chat.recruits_use_boomsticks.weapons_away",
                changed,
                armed);
    }

    /** Whether the recruit carries a supported firearm anywhere: hands, storage, or armour slots. */
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
            if (adapters.isSupportedWeapon(inventory.getItem(slot))) {
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

        boolean carrying = draw ? drawFirearm(recruit) : stowFirearm(recruit);
        setCarrying(recruit, carrying);
        sync(recruit);
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

    /** Sends the current flag to a single player that just started tracking the recruit. */
    public static void sync(CrossBowmanEntity recruit, ServerPlayer target) {
        BoomstickNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> target),
                new CarryFirearmStateMessage(recruit.getId(), isCarrying(recruit)));
    }

    public static boolean isCarrying(CrossBowmanEntity recruit) {
        CompoundTag data = recruit.getPersistentData();
        return data.getBoolean(CARRY_TAG);
    }

    private static void sync(CrossBowmanEntity recruit) {
        BoomstickNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> recruit),
                new CarryFirearmStateMessage(recruit.getId(), isCarrying(recruit)));
    }

    private static void setCarrying(CrossBowmanEntity recruit, boolean carrying) {
        CompoundTag data = recruit.getPersistentData();
        if (carrying) {
            data.putBoolean(CARRY_TAG, true);
        } else {
            data.remove(CARRY_TAG);
        }
    }

    private static boolean drawFirearm(CrossBowmanEntity recruit) {
        RecruitWeaponAdapters adapters = RecruitWeaponAdapters.production();
        if (adapters.isSupportedEnabledWeapon(recruit.getMainHandItem())) {
            return true;
        }
        // Deliberately not Recruits' own `switchMainHandItem`: that one starts its scan at slot 6, so
        // a firearm the player dropped into the recruit's off-hand slot is invisible to it.
        return swapIntoMainHand(recruit, adapters::isSupportedEnabledWeapon);
    }

    private static boolean stowFirearm(CrossBowmanEntity recruit) {
        RecruitWeaponAdapters adapters = RecruitWeaponAdapters.production();
        if (!adapters.isSupportedWeapon(recruit.getMainHandItem())) {
            return false;
        }
        // A recruit that has another weapon takes that one back, so stowing does not disarm it. The
        // melee test is Recruits' own: its melee goal equips a sword or an axe and nothing else, and
        // a crossbowman may also hold its crossbow. Only a recruit that owns nothing else it may hold
        // ends up with an empty hand. The config switches are deliberately ignored: a firearm equipped
        // while the integration was still on has to be stowable after it was switched off.
        // A recruit whose only weapon is the firearm keeps holding it. Emptying its hand would disarm
        // a whole company to obey an order about how it carries its weapons.
        if (!swapIntoMainHand(recruit, stack -> isMeleeWeapon(stack) && !adapters.isSupportedWeapon(stack))) {
            swapIntoMainHand(recruit, stack -> stack.getItem() instanceof CrossbowItem);
        }
        return adapters.isSupportedWeapon(recruit.getMainHandItem());
    }

    /**
     * Puts the first stored stack the test accepts into the main hand and stores what was held.
     *
     * <p>The recruit's equipment slots are backed by inventory slots, so both ends of the swap go
     * through the entity's own hand setters wherever a hand is involved and through the container
     * everywhere else. Armour slots are never a source: nothing that belongs in a hand lives there.</p>
     */
    private static boolean swapIntoMainHand(CrossBowmanEntity recruit, Predicate<ItemStack> wanted) {
        SimpleContainer inventory = recruit.getInventory();
        if (inventory == null) {
            return false;
        }
        int mainSlot = recruit.getInventorySlotIndex(EquipmentSlot.MAINHAND);
        int offSlot = recruit.getInventorySlotIndex(EquipmentSlot.OFFHAND);
        ItemStack held = recruit.getMainHandItem().copy();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == mainSlot || isArmourSlot(recruit, slot)) {
                continue;
            }
            ItemStack candidate = inventory.getItem(slot);
            if (!wanted.test(candidate)) {
                continue;
            }
            if (slot == offSlot && candidate.isEmpty()) {
                // An empty off hand is not storage: parking the firearm there would just move it to
                // the other hand.
                continue;
            }
            ItemStack taken = candidate.copy();
            if (slot == offSlot) {
                recruit.setItemInHand(InteractionHand.OFF_HAND, held);
            } else {
                inventory.setItem(slot, held);
            }
            recruit.setItemInHand(InteractionHand.MAIN_HAND, taken);
            return true;
        }
        return false;
    }

    private static boolean isArmourSlot(CrossBowmanEntity recruit, int slot) {
        EquipmentSlot equipment = recruit.getEquipmentSlotIndex(slot);
        return equipment != null && equipment.getType() == EquipmentSlot.Type.ARMOR;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
}
