package ca.saboor.lumistones;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoneAccessTest {
    private final Lumistones plugin = mock(Lumistones.class, CALLS_REAL_METHODS);
    private final Player player = mock(Player.class);
    private final UUID visitor = UUID.randomUUID();
    private final Map<UUID, Set<UUID>> discoveries = new HashMap<>();
    private final Stone stone = new Stone(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0, "Home", false);
    @BeforeEach void setup() throws Exception {
        when(player.getUniqueId()).thenReturn(visitor);
        var field = Lumistones.class.getDeclaredField("discoveries"); field.setAccessible(true); field.set(plugin, discoveries);
    }
    @Test void bodyMaterialSurvivesOtherOwnerEdits() {
        assertEquals(org.bukkit.Material.POLISHED_ANDESITE, stone.bodyBlock());
        var updated = stone.material(org.bukkit.Material.BRICKS).named("New name").visibility(true);
        assertEquals(org.bukkit.Material.BRICKS, updated.bodyBlock());
        assertEquals(stone.id(), updated.id()); assertEquals(stone.owner(), updated.owner());
    }
    @Test void destinationRequiresPersonalActivationIncludingOwnerAndAdministrator() {
        assertFalse(plugin.known(player, stone));
        when(player.getUniqueId()).thenReturn(stone.owner());
        when(player.hasPermission("lumistones.admin")).thenReturn(true);
        assertFalse(plugin.known(player, stone));
        discoveries.put(stone.owner(), Set.of(stone.id()));
        assertTrue(plugin.known(player, stone));
    }
    @Test void privacyBlocksPreviousVisitorsAndRestoringAccessKeepsDiscovery() {
        discoveries.put(visitor, Set.of(stone.id()));
        assertTrue(plugin.known(player, stone));
        assertFalse(plugin.canUse(player, stone.visibility(true)));
        assertFalse(plugin.known(player, stone.visibility(true)));
        assertTrue(plugin.known(player, stone.visibility(true).visibility(false)));
    }
    @Test void privateStoneAllowsOnlyItsOwnerEvenWhenVisitorIsAdministrator() {
        var restricted = stone.visibility(true);
        when(player.hasPermission("lumistones.admin")).thenReturn(true);
        discoveries.put(visitor, Set.of(stone.id()));
        assertFalse(plugin.canUse(player, restricted));
        assertFalse(plugin.known(player, restricted));
        when(player.getUniqueId()).thenReturn(stone.owner());
        assertTrue(plugin.canUse(player, restricted));
        assertFalse(plugin.known(player, restricted));
        discoveries.put(stone.owner(), Set.of(stone.id()));
        assertTrue(plugin.known(player, restricted));
    }
}
