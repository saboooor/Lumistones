package ca.saboor.lumistones;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelRulesTest {
    @Test void crossDimensionCostsSupportMoneyAndLegacySettings() {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        assertEquals(new Cost(3, 0), Lumistones.crossDimensionCost(config));
        config.set("xp.cross-world-levels", 7);
        assertEquals(new Cost(7, 0), Lumistones.crossDimensionCost(config));
        config.set("xp.cross-world-cost.money", 20);
        assertEquals(new Cost(0, 20), Lumistones.crossDimensionCost(config));
        config.set("xp.cross-dimension-cost.levels", 5);
        config.set("xp.cross-dimension-cost.money", 50);
        assertEquals(new Cost(5, 50), Lumistones.crossDimensionCost(config));
    }

    @Test void distanceCostsRoundUpAndRespectCaps() {
        assertEquals(1, TravelRules.cost(0, false, 1000, 1, 3, 3));
        assertEquals(1, TravelRules.cost(1000, false, 1000, 1, 3, 3));
        assertEquals(2, TravelRules.cost(1000.01, false, 1000, 1, 3, 3));
        assertEquals(3, TravelRules.cost(1_000_000, false, 1000, 1, 3, 3));
        assertEquals(7, TravelRules.cost(0, true, 1000, 1, 3, 7));
    }
    @Test void malformedConfigurationCannotCreateNegativeOrInfiniteCosts() {
        assertEquals(0, TravelRules.cost(1000, false, 0, -1, -3, -3));
        assertEquals(3, TravelRules.cost(1000, false, 0, 7, 3, 3));
        assertEquals(0, TravelRules.cost(0, true, 0, -1, -3, -3));
    }
    @Test void spacingAppliesOnlyWithinWorldAndAllowsExactBoundary() {
        var world = java.util.UUID.randomUUID();
        assertTrue(TravelRules.tooClose(world, 0, 64, 0, world, 63, 64, 0, 64));
        assertFalse(TravelRules.tooClose(world, 0, 64, 0, world, 64, 64, 0, 64));
        assertFalse(TravelRules.tooClose(world, 0, 64, 0, java.util.UUID.randomUUID(), 0, 64, 0, 64));
        assertFalse(TravelRules.tooClose(world, 0, 64, 0, world, 0, 64, 0, 0));
    }
    @Test void userNamesCannotInjectFormattingOrControlCharacters() {
        assertEquals("Spawn kill e", TravelRules.cleanName(" Spawn\n /kill @e § "));
        assertEquals("Café 世界", TravelRules.cleanName("Café 世界"));
        assertEquals(32, TravelRules.cleanName("x".repeat(100)).length());
        assertEquals("", TravelRules.cleanName("§\n/@"));
    }
}
