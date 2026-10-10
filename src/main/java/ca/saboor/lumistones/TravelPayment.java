package ca.saboor.lumistones;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

final class TravelPayment {
    private TravelPayment() {}
    static boolean complete(Player player, Location destination, Cost cost, ItemStack scroll) {
        if (!EconomyPayment.affordable(player, cost) || !EconomyPayment.transaction("withdrawPlayer", player, cost.money())) return false;
        boolean teleported = false;
        try { teleported = complete(player, destination, cost.levels(), scroll); return teleported; }
        finally { if (!teleported) EconomyPayment.refund(player, new Cost(0, cost.money())); }
    }
    static boolean complete(Player player, Location destination, int levels, ItemStack scroll) {
        if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) return false;
        if (levels > 0) player.giveExpLevels(-levels);
        if (scroll != null && player.getGameMode() != GameMode.CREATIVE) scroll.setAmount(scroll.getAmount() - 1);
        return true;
    }
}
