package ca.saboor.lumistones;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import java.util.UUID;

record Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess, Material bodyBlock, int anchorHeight, boolean ownerHead, boolean publicAccess, String menuIcon) {
    Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess, Material bodyBlock, int anchorHeight, boolean ownerHead, boolean publicAccess) {
        this(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight, ownerHead, publicAccess, "TRAVEL");
    }
    DialogView.Sprite customSprite() {
        if (menuIcon == null || !menuIcon.startsWith("sprite|")) return null;
        String[] parts = menuIcon.split("\\|", -1);
        try { return parts.length == 3 ? DialogView.Sprite.parse(parts[1], parts[2]) : null; }
        catch (IllegalArgumentException ignored) { return null; }
    }
    DialogView.Icon icon() {
        try { return DialogView.Icon.valueOf(menuIcon); }
        catch (IllegalArgumentException | NullPointerException ignored) { return DialogView.Icon.TRAVEL; }
    }
    Stone withIcon(String icon) { return new Stone(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight, ownerHead, publicAccess, icon); }
    Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess, Material bodyBlock, int anchorHeight, boolean ownerHead) {
        this(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight, ownerHead, false);
    }
    Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess, Material bodyBlock, int anchorHeight) {
        this(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight, false);
    }
    Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess) {
        this(id, owner, world, x, y, z, name, privateAccess, Material.POLISHED_ANDESITE, 2);
    }
    Stone(UUID id, UUID owner, UUID world, int x, int y, int z, String name, boolean privateAccess, Material bodyBlock) {
        this(id, owner, world, x, y, z, name, privateAccess, bodyBlock, 2);
    }
    Stone material(Material block) { return new Stone(id, owner, world, x, y, z, name, privateAccess, block, anchorHeight, ownerHead, publicAccess, menuIcon); }
    Stone withOwnerHead(boolean enabled) { return new Stone(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight, enabled, publicAccess, menuIcon); }
    Location location() { return new Location(Bukkit.getWorld(world), x, y, z); }
    Stone named(String name) { return new Stone(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight, ownerHead, publicAccess, menuIcon); }
    Stone visibility(boolean privateAccess) { return new Stone(id, owner, world, x, y, z, name, privateAccess, bodyBlock, anchorHeight, ownerHead, publicAccess && !privateAccess, menuIcon); }
    Stone withPublicAccess(boolean enabled) { return new Stone(id, owner, world, x, y, z, name, enabled ? false : privateAccess, bodyBlock, anchorHeight, ownerHead, enabled, menuIcon); }
}
