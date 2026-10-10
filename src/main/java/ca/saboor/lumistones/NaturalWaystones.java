package ca.saboor.lumistones;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs after population on the server thread; never generates neighboring chunks. */
final class NaturalWaystones implements Listener {
    private final Lumistones plugin;
    private final NamespacedKey eligible;
    private final File file;
    private final Set<String> finished = new HashSet<>();
    NaturalWaystones(Lumistones plugin) {
        this.plugin = plugin;
        eligible = new NamespacedKey(plugin, "natural_generation");
        file = new File(plugin.getDataFolder(), "natural-generation.yml");
    }
    void enable() {
        finished.addAll(DataFiles.read(file).getStringList("finished"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void populate(ChunkPopulateEvent event) {
        // Remember newly populated chunks so an unload before the scheduled scan is harmless.
        event.getChunk().getPersistentDataContainer().set(eligible, PersistentDataType.BYTE, (byte)1);
        schedule(event.getChunk());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void load(ChunkLoadEvent event) {
        if (event.getChunk().getPersistentDataContainer().has(eligible, PersistentDataType.BYTE)) schedule(event.getChunk());
    }
    private void schedule(Chunk chunk) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded() && plugin.getConfig().getBoolean("natural-generation.enabled", true)) scan(chunk);
        });
    }
    void scan(Chunk chunk) {
        World world = chunk.getWorld();
        if (!plugin.worldEnabled(world)) return;
        List<String> worlds = plugin.getConfig().getStringList("natural-generation.worlds");
        if (!worlds.isEmpty() && !worlds.contains(world.getName())) return;
        for (var structure : chunk.getStructures()) {
            NamespacedKey structureKey = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.STRUCTURE).getKey(structure.getStructure());
            if (structureKey == null) continue;
            String type = structureKey.toString();
            BoundingBox box = structure.getBoundingBox();
            UUID id = structureId(world.getUID(), type, box);
            if (finished.contains(id.toString())) continue;
            if (plugin.stones.containsKey(id)) { finish(id); continue; }
            double chance = plugin.getConfig().getDouble("natural-generation.structures." + type, 0);
            if (!passes(id, chance)) { finish(id); continue; }
            Block site = findSite(chunk, box);
            if (site == null) continue; // Same deterministic roll when another part becomes populated.
            String name = WaystoneNames.forStone(id);
            Stone stone = new Stone(id, new UUID(0, 0), world.getUID(), site.getX(), site.getY(), site.getZ(), name,
                    false, org.bukkit.Material.POLISHED_ANDESITE, plugin.anchorHeight());
            plugin.createAnchors(stone);
            plugin.stones.put(id, stone);
            plugin.persist();
            finish(id);
            plugin.models().spawn(stone);
        }
    }
    private Block findSite(Chunk chunk, BoundingBox box) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight() + 1, (int)Math.floor(box.getMinY()));
        int maxY = Math.min(world.getMaxHeight() - plugin.clearanceHeight(), (int)Math.ceil(box.getMaxY()) + 1);
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) {
            int wx = (chunk.getX() << 4) + x, wz = (chunk.getZ() << 4) + z;
            if (wx < box.getMinX() || wx > box.getMaxX() || wz < box.getMinZ() || wz > box.getMaxZ()) continue;
            for (int y = maxY; y >= minY; y--) {
                Block site = chunk.getBlock(x, y, z);
                if (!floor(chunk.getBlock(x, y - 1, z)) || !site.getType().isAir()
                        || !chunk.getBlock(x, y + 1, z).getType().isAir() || !chunk.getBlock(x, y + 2, z).getType().isAir()
                        || !world.getWorldBorder().isInside(site.getLocation()) || plugin.tooClose(site.getLocation())) continue;
                boolean clear = true;
                for (int dy = 3; dy < plugin.clearanceHeight(); dy++) if (!chunk.getBlock(x, y + dy, z).getType().isAir()) clear = false;
                if (!clear) continue;
                // A clear neighboring landing column is required, inside this already loaded chunk.
                boolean landing = false;
                for (int[] delta : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int nx = x + delta[0], nz = z + delta[1];
                    if (floor(chunk.getBlock(nx, y - 1, nz)) && chunk.getBlock(nx, y, nz).getType().isAir()
                            && chunk.getBlock(nx, y + 1, nz).getType().isAir()) landing = true;
                }
                if (landing) return site;
            }
        }
        return null;
    }
    private boolean floor(Block block) {
        Material type = block.getType();
        return type.isSolid() && !Tag.LEAVES.isTagged(type) && !Tag.LOGS.isTagged(type)
                && type != Material.MAGMA_BLOCK && type != Material.CACTUS && type != Material.CAMPFIRE
                && type != Material.SOUL_CAMPFIRE && type != Material.BARRIER;
    }
    private void finish(UUID id) {
        finished.add(id.toString());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("finished", finished.stream().sorted().toList());
        DataFiles.write(plugin, file.toPath(), yaml);
    }
    static UUID structureId(UUID world, String type, BoundingBox box) {
        return UUID.nameUUIDFromBytes(("lumistones:natural:" + world + ":" + type + ":" + box.getMinX() + ":"
                + box.getMinY() + ":" + box.getMinZ() + ":" + box.getMaxX() + ":" + box.getMaxY() + ":" + box.getMaxZ())
                .getBytes(StandardCharsets.UTF_8));
    }
    static boolean passes(UUID id, double percent) {
        if (!Double.isFinite(percent) || percent <= 0) return false;
        return new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits()).nextDouble() * 100 < Math.min(100, percent);
    }
}
