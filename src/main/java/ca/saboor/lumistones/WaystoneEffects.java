package ca.saboor.lumistones;

import org.bukkit.*;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import java.time.Duration;

final class WaystoneEffects {
    private final Lumistones plugin;
    WaystoneEffects(Lumistones plugin) { this.plugin = plugin; }
    void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::ambient, 20, 20);
    }
    private void ambient() {
        if (!plugin.getConfig().getBoolean("effects.enchantment-particles", true)) return;
        for (Stone stone : plugin.stones.values()) {
            World world = Bukkit.getWorld(stone.world());
            if (!plugin.worldEnabled(world) || !world.isChunkLoaded(stone.x() >> 4, stone.z() >> 4)) continue;
            Location center = stone.location().add(.5, 1.2, .5);
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(center) > 32 * 32) continue;
                player.spawnParticle(Particle.ENCHANT, center, 12, .65, .8, .65, .3);
            }
        }
    }
    void arrived(Player player, Location departure, String name) {
        if (plugin.getConfig().getBoolean("effects.teleport-particles", true)) {
            burst(departure);
            burst(player.getLocation());
            // A ring around the arriving player gives the burst a distinct shape.
            Location center = player.getLocation().add(0, 1, 0);
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12;
                player.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(Math.cos(angle) * .8, 0, Math.sin(angle) * .8), 2, .05, .3, .05, .03);
            }
        }
        if (plugin.getConfig().getBoolean("effects.teleport-title", true)) {
            player.showTitle(Title.title(Component.text(name, NamedTextColor.LIGHT_PURPLE), Component.empty(),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(600), Duration.ofMillis(250))));
        }
    }
    private void burst(Location location) {
        if (location.getWorld() != null) location.getWorld().spawnParticle(Particle.PORTAL, location.clone().add(0, 1, 0), 70, .5, .8, .5, .6);
    }
}
