package ca.saboor.lumistones;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

record ModelSettings(List<Part> parts, boolean labelEnabled, float labelScale, float labelRange, String runeText, float runeScale, boolean runesEnabled) {
    record Vector(float x, float y, float z) {}
    record Part(String id, boolean bodyBlock, boolean item, Material material, Vector translation, Vector rotation, Vector scale, int customModelData) {}
    static ModelSettings read(ConfigurationSection yaml) {
        var section = yaml.getConfigurationSection("parts");
        if (section == null || section.getKeys(false).isEmpty()) throw new IllegalArgumentException("model.yml needs at least one part");
        if (section.getKeys(false).size() > 128) throw new IllegalArgumentException("At most 128 model parts are allowed");
        List<Part> parts = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            var part = section.getConfigurationSection(id);
            if (part == null) throw new IllegalArgumentException("Invalid model part: " + id);
            String type = part.getString("type", "block");
            if (!type.equals("block") && !type.equals("item")) throw new IllegalArgumentException("Part " + id + " type must be block or item");
            Material material = Material.matchMaterial(part.getString("material", "STONE"));
            if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR)
                throw new IllegalArgumentException("Invalid material for part " + id);
            boolean item = type.equals("item");
            int custom = part.getInt("custom-model-data", 0);
            if (custom < 0) throw new IllegalArgumentException("custom-model-data must be nonnegative");
            parts.add(new Part(id, "body_block".equals(part.getString("material-placeholder")), item, material, vector(part, "translation", 0, false), vector(part, "rotation", 0, false), vector(part, "scale", 1, true), custom));
        }
        return new ModelSettings(List.copyOf(parts), yaml.getBoolean("label.enabled", true), number(yaml, "label.scale", .4f, true), number(yaml, "label.view-range", .5f, true), yaml.getString("runes.text", "&fᚠ ᚢ\nᚦ ᚨ\nᚱ ᚲ"), number(yaml, "runes.scale", 1f, true), yaml.getBoolean("runes.enabled", true));
    }
    private static Vector vector(ConfigurationSection part, String key, float fallback, boolean positive) {
        if (!part.contains(key)) return new Vector(fallback, fallback, fallback);
        List<?> list = part.getList(key);
        if (list == null || list.size() != 3) throw new IllegalArgumentException(key + " needs [x, y, z]");
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.get(i) instanceof Number n)) throw new IllegalArgumentException(key + " values must be numbers");
            values[i] = checked(n.floatValue(), key, positive);
        }
        return new Vector(values[0], values[1], values[2]);
    }
    private static float number(ConfigurationSection config, String key, float fallback, boolean positive) {
        if (!config.contains(key)) return fallback;
        Object value = config.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(key + " must be a number");
        return checked(number.floatValue(), key, positive);
    }
    private static float checked(float value, String key, boolean positive) {
        if (!Float.isFinite(value) || Math.abs(value) > 360 || positive && value <= 0)
            throw new IllegalArgumentException("Invalid " + key + " value " + value);
        return value == 0 ? 0f : value;
    }
}
