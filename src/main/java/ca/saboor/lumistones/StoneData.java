package ca.saboor.lumistones;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Portable stone metadata, stored on the retrieved item across server restarts. */
record StoneData(Stone stone, Set<UUID> discoverers) {
    String encode() {
        var data = new YamlConfiguration();
        data.set("id", stone.id().toString()); data.set("owner", stone.owner().toString());
        data.set("name", stone.name()); data.set("private", stone.privateAccess());
        data.set("public", stone.publicAccess()); data.set("body", stone.bodyBlock().name());
        data.set("height", stone.anchorHeight()); data.set("head", stone.ownerHead());
        data.set("icon", stone.menuIcon());
        data.set("discoverers", discoverers.stream().map(UUID::toString).sorted().toList());
        return data.saveToString();
    }
    static StoneData decode(String encoded, UUID world, int x, int y, int z) {
        try {
            var data = new YamlConfiguration(); data.loadFromString(encoded);
            var stone = new Stone(UUID.fromString(data.getString("id")), UUID.fromString(data.getString("owner")),
                    world, x, y, z, data.getString("name"), data.getBoolean("private"),
                    Material.valueOf(data.getString("body")), Math.max(1, Math.min(16, data.getInt("height"))),
                    data.getBoolean("head"), data.getBoolean("public"), data.getString("icon"));
            Set<UUID> discoverers = new HashSet<>();
            for (String id : data.getStringList("discoverers")) discoverers.add(UUID.fromString(id));
            return new StoneData(stone, Set.copyOf(discoverers));
        } catch (Exception exception) { throw new IllegalArgumentException("Invalid saved lumistone", exception); }
    }
}
