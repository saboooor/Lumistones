package ca.saboor.lumistones;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** Server-thread visit history, independent of stone discovery and stone relocation. */
final class WorldDiscoveries {
    private final Map<UUID, Set<UUID>> worlds = new HashMap<>();

    boolean visit(UUID player, UUID world) {
        return worlds.computeIfAbsent(player, ignored -> new HashSet<>()).add(world);
    }

    boolean visited(UUID player, UUID world) {
        return worlds.getOrDefault(player, Set.of()).contains(world);
    }

    void load(ConfigurationSection data) {
        worlds.clear();
        var section = data.getConfigurationSection("world-discoveries");
        if (section == null) return;
        for (String player : section.getKeys(false)) {
            for (String world : section.getStringList(player)) visit(UUID.fromString(player), UUID.fromString(world));
        }
    }

    void save(ConfigurationSection data) {
        data.set("world-discoveries", null);
        worlds.forEach((player, ids) -> data.set("world-discoveries." + player, ids.stream().map(UUID::toString).sorted().toList()));
    }
}
