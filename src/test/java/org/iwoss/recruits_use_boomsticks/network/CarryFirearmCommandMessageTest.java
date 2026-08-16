package org.iwoss.recruits_use_boomsticks.network;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarryFirearmCommandMessageTest {
    @Test
    void oneMessageCarriesEverySelectedGroup() {
        List<UUID> groups = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        CarryFirearmCommandMessage message = new CarryFirearmCommandMessage(groups, true);

        assertEquals(groups, message.groups());
        assertThrows(UnsupportedOperationException.class,
                () -> message.groups().add(UUID.randomUUID()));
    }

    @Test
    void serverCooldownRejectsPacketSpam() {
        CompoundTag playerData = new CompoundTag();

        long first = 100L;
        long tooSoon = first + CarryFirearmCommandMessage.COMMAND_COOLDOWN_TICKS - 1L;
        long allowed = first + CarryFirearmCommandMessage.COMMAND_COOLDOWN_TICKS;

        assertTrue(CarryFirearmCommandMessage.acquire(playerData, first));
        assertFalse(CarryFirearmCommandMessage.acquire(playerData, first));
        assertFalse(CarryFirearmCommandMessage.acquire(playerData, tooSoon));
        assertTrue(CarryFirearmCommandMessage.acquire(playerData, allowed));
    }

    @Test
    void twoConsecutiveHumanClicksBothReachTheServer() {
        // Draw and stow sit next to each other on the command screen. A player pressing both must
        // not have the second order silently dropped.
        CompoundTag playerData = new CompoundTag();

        assertTrue(CarryFirearmCommandMessage.acquire(playerData, 0L));
        assertTrue(CarryFirearmCommandMessage.acquire(playerData, 4L));
    }

    @Test
    void messageAcceptsExactlyTheSelectionTheSenderCutsTo() {
        // The command screen cuts an oversized selection down to this many groups, so the longest
        // list it can hand over has to be a legal message rather than an exception thrown inside
        // the button handler, which would take the screen down instead of sending the order.
        List<UUID> full = new java.util.ArrayList<>();
        for (int index = 0; index < CarryFirearmCommandMessage.MAX_GROUPS; index++) {
            full.add(UUID.randomUUID());
        }

        assertEquals(
                CarryFirearmCommandMessage.MAX_GROUPS,
                new CarryFirearmCommandMessage(full, false).groups().size());
    }

    @Test
    void messageRejectsAnUnboundedGroupList() {
        assertThrows(IllegalArgumentException.class,
                () -> new CarryFirearmCommandMessage(List.of(), true));
        assertThrows(IllegalArgumentException.class,
                () -> new CarryFirearmCommandMessage(
                        java.util.Collections.nCopies(
                                CarryFirearmCommandMessage.MAX_GROUPS + 1,
                                UUID.randomUUID()),
                        true));
    }
}
