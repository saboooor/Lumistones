package ca.saboor.lumistones;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CostTest {
    @Test void supportsEitherCurrencyAndBoth() {
        var config = new YamlConfiguration();
        config.set("cost.money", 12.5);
        assertEquals(new Cost(0, 12.5), Cost.read(config, "cost", null, 3));
        config.set("cost.levels", 2);
        assertEquals(new Cost(2, 12.5), Cost.read(config, "cost", null, 3));
    }
    @Test void explicitMoneyOnlyOverridesBundledLevelDefaults() {
        var config = new YamlConfiguration();
        config.addDefault("cost.levels", 3);
        config.set("cost.money", 20);
        assertEquals(new Cost(0, 20), Cost.read(config, "cost", null, 3));
    }
    @Test void legacyLevelsOverrideBundledDefaultsUntilReplaced() {
        var config = new YamlConfiguration();
        config.addDefault("cost.levels", 3);
        config.set("old-levels", 7);
        assertEquals(new Cost(7, 0), Cost.read(config, "cost", "old-levels", 3));
        config.set("cost.money", 10);
        assertEquals(new Cost(0, 10), Cost.read(config, "cost", "old-levels", 3));
    }
    @Test void clampsNegativeAndNonfiniteCosts() {
        assertEquals(Cost.FREE, new Cost(-2, Double.NaN));
        assertEquals(Cost.FREE, new Cost(-2, -10));
    }
}
