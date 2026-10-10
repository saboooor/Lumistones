package ca.saboor.lumistones;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TravelPaymentTest {
    @Test void deniedTeleportDoesNotChargeExperienceOrConsumeScroll() {
        Player player = mock(Player.class); ItemStack scroll = mock(ItemStack.class); Location destination = new Location(null, 1, 2, 3);
        when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)).thenReturn(false);
        assertFalse(TravelPayment.complete(player, destination, 3, scroll));
        verify(player, never()).giveExpLevels(anyInt()); verify(scroll, never()).setAmount(anyInt());
    }
    @Test void successfulTeleportChargesOnceAfterTeleport() {
        Player player = mock(Player.class); ItemStack scroll = mock(ItemStack.class); Location destination = new Location(null, 1, 2, 3);
        when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(scroll.getAmount()).thenReturn(4);
        assertTrue(TravelPayment.complete(player, destination, 2, scroll));
        var order = inOrder(player, scroll); order.verify(player).teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        order.verify(player).giveExpLevels(-2); order.verify(scroll).setAmount(3);
    }
    @Test void creativeFreeTravelKeepsScroll() {
        Player player = mock(Player.class); ItemStack scroll = mock(ItemStack.class); Location destination = new Location(null, 1, 2, 3);
        when(player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)).thenReturn(true); when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertTrue(TravelPayment.complete(player, destination, 0, scroll));
        verify(player, never()).giveExpLevels(anyInt()); verify(scroll, never()).setAmount(anyInt());
    }
}
