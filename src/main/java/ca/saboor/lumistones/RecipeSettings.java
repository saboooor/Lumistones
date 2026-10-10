package ca.saboor.lumistones;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

record RecipeSettings(List<String> shape, Map<Character, Material> ingredients) {
    static RecipeSettings read(ConfigurationSection config) {
        List<String> shape = config.getStringList("recipe.shape");
        if (shape.isEmpty()) shape = List.of("SSS", "SES", "OOO");
        final int rowWidth = shape.getFirst().length();
        if (shape.size() > 3 || shape.getFirst().isEmpty() || shape.getFirst().length() > 3 || shape.stream().anyMatch(row -> row.length() != rowWidth)) throw new IllegalArgumentException("recipe.shape must be a rectangular 1–3 by 1–3 grid");
        Map<Character, Material> ingredients = new LinkedHashMap<>();
        var section = config.getConfigurationSection("recipe.ingredients");
        if (section == null) ingredients.putAll(Map.of('S', Material.STONE_BRICKS, 'E', Material.ENDER_PEARL, 'O', Material.OBSIDIAN));
        else for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(section.getString(key, ""));
            if (key.length() != 1 || key.equals(" ") || material == null || (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR)) throw new IllegalArgumentException("Invalid recipe ingredient: " + key);
            ingredients.put(key.charAt(0), material);
        }
        Set<Character> used = new HashSet<>(); shape.forEach(row -> row.chars().filter(c -> c != ' ').forEach(c -> used.add((char)c)));
        if (used.isEmpty() || !ingredients.keySet().containsAll(used)) throw new IllegalArgumentException("Every recipe symbol needs an ingredient");
        ingredients.keySet().retainAll(used);
        return new RecipeSettings(List.copyOf(shape), Map.copyOf(ingredients));
    }
}
