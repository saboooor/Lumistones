package ca.saboor.lumistones;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Optional Vault bridge; level-only servers do not need Vault installed. */
final class EconomyPayment {
    private EconomyPayment() {}
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object invoke(String method, Player player, double money) throws ReflectiveOperationException {
        Class economy = Class.forName("net.milkbowl.vault.economy.Economy");
        var registration = Bukkit.getServicesManager().getRegistration(economy);
        if (registration == null) throw new IllegalStateException("No economy provider");
        return economy.getMethod(method, OfflinePlayer.class, double.class).invoke(registration.getProvider(), player, money);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String format(double amount) {
        try {
            Class economy = Class.forName("net.milkbowl.vault.economy.Economy");
            var registration = Bukkit.getServicesManager().getRegistration(economy);
            if (registration != null) {
                Object formatted = economy.getMethod("format", double.class).invoke(registration.getProvider(), amount);
                if (formatted instanceof String text && !text.isBlank()) return text;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return "$" + java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }
    static boolean affordable(Player player, Cost cost) {
        if (player.getLevel() < cost.levels()) return false;
        if (cost.money() == 0) return true;
        try { return Boolean.TRUE.equals(invoke("has", player, cost.money())); }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    static boolean transaction(String method, Player player, double money) {
        if (money == 0) return true;
        try {
            Object response = invoke(method, player, money);
            return Boolean.TRUE.equals(response.getClass().getMethod("transactionSuccess").invoke(response));
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    static boolean charge(Player player, Cost cost) {
        if (!affordable(player, cost) || !transaction("withdrawPlayer", player, cost.money())) return false;
        if (cost.levels() > 0) player.giveExpLevels(-cost.levels());
        return true;
    }
    static void refund(Player player, Cost cost) {
        if (cost.levels() > 0) player.giveExpLevels(cost.levels());
        if (!transaction("depositPlayer", player, cost.money()))
            Bukkit.getLogger().severe("Lumistones could not refund " + cost.money() + " to " + player.getUniqueId());
    }
}
