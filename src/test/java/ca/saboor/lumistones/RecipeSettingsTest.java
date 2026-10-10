package ca.saboor.lumistones;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecipeSettingsTest {
    @Test void malformedShapeFailsBeforeRecipeRegistration() {
        var yaml = new YamlConfiguration(); yaml.set("recipe.shape", List.of("AA", "A"));
        assertThrows(IllegalArgumentException.class, () -> RecipeSettings.read(yaml));
        yaml.set("recipe.shape", List.of("AAAA")); assertThrows(IllegalArgumentException.class, () -> RecipeSettings.read(yaml));
    }
    @Test void customRecipeCanBeConfigured() {
        var yaml = new YamlConfiguration(); yaml.set("recipe.shape", List.of(" E ", "OLO", " D "));
        yaml.set("recipe.ingredients.E", "ENDER_PEARL"); yaml.set("recipe.ingredients.O", "OBSIDIAN"); yaml.set("recipe.ingredients.L", "LODESTONE"); yaml.set("recipe.ingredients.D", "DIAMOND");
        var settings = RecipeSettings.read(yaml); assertEquals(Material.LODESTONE, settings.ingredients().get('L')); assertEquals(4, settings.ingredients().size());
    }
    @Test void missingIngredientRejectsRecipe() {
        var yaml = new YamlConfiguration(); yaml.set("recipe.shape", List.of(" A ")); yaml.set("recipe.ingredients.E", "ENDER_PEARL");
        assertThrows(IllegalArgumentException.class, () -> RecipeSettings.read(yaml));
    }
}
