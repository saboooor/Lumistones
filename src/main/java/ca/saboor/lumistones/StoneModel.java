package ca.saboor.lumistones;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import java.util.*;
import java.io.File;

final class StoneModel {
    private final Lumistones plugin;
    private ModelSettings settings;
    private final Map<UUID, List<TextDisplay>> overlays = new HashMap<>();
    StoneModel(Lumistones plugin) {
        this.plugin = plugin;
        if (!new File(plugin.getDataFolder(), "model.yml").exists()) plugin.saveResource("model.yml", false);
        settings = readSettings();
    }
    private ModelSettings readSettings() {
        ModelSettings updated = ModelSettings.read(DataFiles.read(new File(plugin.getDataFolder(), "model.yml")));
        for (var part : updated.parts()) {
            if (part.item() && !part.material().isItem()) throw new IllegalArgumentException("Item parts require obtainable item materials");
            if (!part.item() && !part.material().isBlock()) throw new IllegalArgumentException("Block parts require block materials");
        }
        return updated;
    }
    boolean reload() {
        try {
            ModelSettings updated = readSettings();
            settings = updated; return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Model reload rejected; keeping previous model: " + exception.getMessage());
            return false;
        }
    }
    UUID stoneId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(plugin.entityKey, PersistentDataType.STRING);
        try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
    }
    void reconcile(Chunk chunk) {
        // Remove only our entities in this chunk, then reconstruct from authoritative records.
        for (Entity entity : chunk.getEntities()) if (stoneId(entity) != null) entity.remove();
        overlays.values().forEach(list -> list.removeIf(entity -> !entity.isValid()));
        overlays.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        for (Stone stone : plugin.stones.values())
            if (stone.world().equals(chunk.getWorld().getUID()) && stone.x() >> 4 == chunk.getX() && stone.z() >> 4 == chunk.getZ()) spawn(stone);
    }
    void remove(Stone stone) {
        overlays.remove(stone.id());
        if (stone.location().getWorld() == null) return;
        for (Entity entity : stone.location().getChunk().getEntities()) if (stone.id().equals(stoneId(entity))) entity.remove();
    }
    private void tag(Entity entity, Stone stone) {
        entity.setPersistent(false); // Rebuilt on chunk load; never leave saved orphan models.
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.getPersistentDataContainer().set(plugin.entityKey, PersistentDataType.STRING, stone.id().toString());
    }
    void spawn(Stone stone) {
        // Upgrade only our previously saved invisible anchors; never overwrite unrelated blocks.
        Location anchor = stone.location();
        if (anchor.getWorld() != null && anchor.getBlock().getType() == Material.BARRIER)
            anchor.getBlock().setType(Material.POLISHED_TUFF_WALL, false);
        if (anchor.getWorld() != null && anchor.getBlock().getType() == Material.POLISHED_TUFF_WALL) plugin.createAnchors(stone);
        Location base = stone.location().add(.5, 0, .5);
        if (base.getWorld() == null) return;
        int custom = plugin.getConfig().getInt("model.custom-model-data");
        if (custom > 0) {
            ItemDisplay display = base.getWorld().spawn(base.clone().add(0, 1, 0), ItemDisplay.class);
            Material material = Material.matchMaterial(plugin.getConfig().getString("model.item-material", "AMETHYST_SHARD"));
            if (material == null || !material.isItem() || material.isAir()) material = Material.AMETHYST_SHARD;
            ItemStack item = new ItemStack(material);
            var meta = item.getItemMeta(); var data = meta.getCustomModelDataComponent(); data.setFloats(List.of((float)custom)); meta.setCustomModelDataComponent(data); item.setItemMeta(meta);
            display.setItemStack(item); display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); tag(display, stone);
        } else {
            for (var part : settings.parts()) {
                spawnPart(stone, base, part);
                if (settings.runesEnabled() && part.id().equals("pillar")) spawnRunes(stone, base, part);
                if (settings.labelEnabled() && part.id().equals("rim")) spawnSides(stone, base, part, "\u00a7d" + stone.name(), true);
            }
        }
        spawnOwnerHead(stone, base);
    }
    private void spawnOwnerHead(Stone stone, Location base) {
        if (!stone.ownerHead() || stone.owner().equals(new UUID(0, 0))) return;
        double height = plugin.getConfig().getDouble("model.owner-head.height", 2.5);
        double scale = plugin.getConfig().getDouble("model.owner-head.scale", .75);
        if (!Double.isFinite(height) || Math.abs(height) > 360 || !Double.isFinite(scale) || scale <= 0 || scale > 16) {
            plugin.getLogger().warning("Invalid model.owner-head height or scale; skipping owner head.");
            return;
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(stone.owner()));
        head.setItemMeta(meta);
        base.getWorld().spawn(base.clone().add(0, height, 0), ItemDisplay.class, display -> {
            display.setItemStack(head);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setTransformationMatrix(new Matrix4f().scale((float)scale));
            display.setShadowRadius(0);
            tag(display, stone);
        });
    }
    private TextDisplay overlay(Stone stone, Location location) {
        // Default visibility is set before insertion/tracking, preventing a first-packet flash.
        TextDisplay entity = location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.setVisibleByDefault(false); tag(display, stone);
        });
        overlays.computeIfAbsent(stone.id(), ignored -> new ArrayList<>()).add(entity);
        for (Player player : Bukkit.getOnlinePlayers()) updateVisibility(plugin, player, stone, entity);
        return entity;
    }
    static void updateVisibility(Lumistones plugin, Player player, Stone stone, Entity overlay) {
        if (plugin.known(player, stone)) player.showEntity(plugin, overlay);
        else player.hideEntity(plugin, overlay);
    }
    void refresh(Player player) {
        overlays.forEach((id, entities) -> {
            Stone stone = plugin.stones.get(id);
            if (stone != null) for (TextDisplay entity : entities) if (entity.isValid()) updateVisibility(plugin, player, stone, entity);
        });
    }
    void refresh(Stone stone) {
        for (TextDisplay entity : overlays.getOrDefault(stone.id(), List.of())) if (entity.isValid())
            for (Player player : Bukkit.getOnlinePlayers()) updateVisibility(plugin, player, stone, entity);
    }
    private void spawnRunes(Stone stone, Location base, ModelSettings.Part pillar) {
        spawnSides(stone, base, pillar, TextStyles.legacy(settings.runeText()), false);
    }
    static float nameScale(ModelSettings.Part rim, String name, float requested, boolean xFace) {
        float width = xFace ? rim.scale().z() : rim.scale().x();
        float textWidth = (float)name.codePoints().mapToDouble(code -> code > 127 ? .4 : .2).sum();
        return Math.min(requested, Math.min(rim.scale().y() * .85f / .25f, width * .9f / Math.max(.2f, textWidth)));
    }
    private void spawnSides(Stone stone, Location base, ModelSettings.Part pillar, String inscription, boolean name) {
        var p = pillar.translation(); var r = pillar.rotation(); var s = pillar.scale();
        Matrix4f rotation = new Matrix4f().rotateXYZ((float)Math.toRadians(r.x()), (float)Math.toRadians(r.y()), (float)Math.toRadians(r.z()));
        // Text faces +Z by default. Anchor each overlay just outside its pillar face.
        float[][] faces = {{s.x()/2, s.y()/2, s.z()+.006f, 0}, {s.x()/2, s.y()/2, -.006f, 180},
                {s.x()+.006f, s.y()/2, s.z()/2, 90}, {-.006f, s.y()/2, s.z()/2, -90}};
        for (float[] face : faces) {
            var point = rotation.transformPosition(new org.joml.Vector3f(face[0], face[1], face[2])).add(p.x(), p.y(), p.z());
            TextDisplay text = overlay(stone, base);
            text.text(TextStyles.component(inscription)); text.setLineWidth(2048);
            if (name) text.setViewRange(settings.labelRange());
            text.setBillboard(Display.Billboard.FIXED); text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setDefaultBackground(false); text.setBackgroundColor(Color.fromARGB(0)); text.setShadowed(false);
            text.setSeeThrough(false); text.setBrightness(new Display.Brightness(15, 15));
            // Center the inscription vertically around the middle of the pillar.
            float lines = Math.max(1, TextStyles.plain(inscription).split("\\n", -1).length);
            float scale = name ? nameScale(pillar, stone.name(), settings.labelScale(), Math.abs(face[3]) == 90) : settings.runeScale();
            text.setTransformationMatrix(new Matrix4f().translation(point).mul(rotation)
                    .rotateY((float)Math.toRadians(face[3])).scale(scale)
                    .translate(0, -lines * .125f, 0));
            tag(text, stone);
        }
    }
    private void spawnPart(Stone stone, Location base, ModelSettings.Part part) {
        Display display;
        if (part.item()) {
            ItemDisplay item = base.getWorld().spawn(base, ItemDisplay.class);
            ItemStack stack = new ItemStack(part.bodyBlock() ? stone.bodyBlock() : part.material());
            if (part.customModelData() > 0) {
                var meta = stack.getItemMeta(); var data = meta.getCustomModelDataComponent(); data.setFloats(List.of((float)part.customModelData())); meta.setCustomModelDataComponent(data); stack.setItemMeta(meta);
            }
            item.setItemStack(stack); item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display = item;
        } else {
            BlockDisplay block = base.getWorld().spawn(base, BlockDisplay.class);
            block.setBlock((part.bodyBlock() ? stone.bodyBlock() : part.material()).createBlockData()); display = block;
        }
        var position = part.translation(); var rotation = part.rotation(); var scale = part.scale();
        display.setTransformationMatrix(new Matrix4f().translation(position.x(), position.y(), position.z())
                .rotateXYZ((float)Math.toRadians(rotation.x()), (float)Math.toRadians(rotation.y()), (float)Math.toRadians(rotation.z()))
                .scale(scale.x(), scale.y(), scale.z()));
        display.setShadowRadius(0); tag(display, stone);
    }
}
