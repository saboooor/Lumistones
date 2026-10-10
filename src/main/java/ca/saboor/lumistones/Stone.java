package ca.saboor.lumistones;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import java.util.UUID;

record Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess, Material bodyBlock, int anchorHeight) {
    Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess) {
        this(id, owner, world, x, y, z, name, privateAccess, Material.POLISHED_ANDESITE, 2);
    }
    Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess, Material bodyBlock) {
        this(id, owner, world, x, y, z, name, privateAccess, bodyBlock, 2);
    }
    Stone material(Material block) { return new Stone(id, owner, world, x, y, z, name, privateAccess, block, anchorHeight); }
    Location location() { return new Location(Bukkit.getWorld(world), x, y, z); }
    Stone named(String name) { return new Stone(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight); }
    Stone visibility(boolean privateAccess) { return new Stone(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight); }
}
