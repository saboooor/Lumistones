package ca.saboor.lumistones;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NaturalWaystonesTest {
    @Test void identitySurvivesReloadsAndDistinguishesStructuresAndWorlds() {
        UUID world = UUID.randomUUID();
        BoundingBox box = new BoundingBox(-40, 60, 20, 80, 100, 120);
        UUID id = NaturalWaystones.structureId(world, "minecraft:village_plains", box);
        assertEquals(id, NaturalWaystones.structureId(world, "minecraft:village_plains", box.clone()));
        assertNotEquals(id, NaturalWaystones.structureId(UUID.randomUUID(), "minecraft:village_plains", box));
        assertNotEquals(id, NaturalWaystones.structureId(world, "minecraft:pillager_outpost", box));
        assertNotEquals(id, NaturalWaystones.structureId(world, "minecraft:village_plains", box.clone().shift(16, 0, 0)));
    }
    @Test void chanceIsOneStableRollWithSafeLimits() {
        UUID id = UUID.randomUUID();
        assertFalse(NaturalWaystones.passes(id, 0));
        assertFalse(NaturalWaystones.passes(id, -1));
        assertFalse(NaturalWaystones.passes(id, Double.NaN));
        assertFalse(NaturalWaystones.passes(id, Double.POSITIVE_INFINITY));
        assertTrue(NaturalWaystones.passes(id, 100));
        assertTrue(NaturalWaystones.passes(id, 150));
        assertEquals(NaturalWaystones.passes(id, 25), NaturalWaystones.passes(id, 25));
        for (int i = 0; i < 100; i++) {
            UUID candidate = new UUID(0, i);
            if (NaturalWaystones.passes(candidate, 25)) assertTrue(NaturalWaystones.passes(candidate, 50));
        }
    }
}
