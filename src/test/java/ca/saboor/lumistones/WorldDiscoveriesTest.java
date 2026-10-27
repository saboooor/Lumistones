package ca.saboor.lumistones;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldDiscoveriesTest {
    final UUID playerId = UUID.randomUUID(), homeId = UUID.randomUUID(), destinationId = UUID.randomUUID();
    final WorldDiscoveries visits = new WorldDiscoveries();
    final Lumistones plugin = mock(Lumistones.class, CALLS_REAL_METHODS);
    final Player player = mock(Player.class);
    final World home = mock(World.class);
    final YamlConfiguration config = new YamlConfiguration();
    final Map<UUID, Set<UUID>> stones = new HashMap<>();
    final Stone target = new Stone(UUID.randomUUID(), playerId, destinationId, 0, 64, 0, "Destination", false);

    @BeforeEach void setup() throws Exception {
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(home);
        when(home.getUID()).thenReturn(homeId);
        doReturn(config).when(plugin).getConfig();
        for (var entry : Map.of("worldDiscoveries", visits, "discoveries", stones).entrySet()) {
            var field = Lumistones.class.getDeclaredField(entry.getKey());
            field.setAccessible(true); field.set(plugin, entry.getValue());
        }
    }

    @Test void disabledAndMissingSettingPreserveExistingTravelAccess() {
        stones.put(playerId, Set.of(target.id()));
        assertTrue(plugin.known(player, target));
        config.set("teleport.require-world-discovery", false);
        assertTrue(plugin.known(player, target));
        config.set("teleport.require-world-discovery", true);
        assertFalse(plugin.known(player, target));
    }

    @Test void globalStonesStillRequireWorldVisitEvenForOwnerAndAdministrator() {
        config.set("waystones.public.enabled", true);
        config.set("teleport.require-world-discovery", true);
        when(player.hasPermission("lumistones.admin")).thenReturn(true);
        Stone global = target.withPublicAccess(true);
        assertFalse(plugin.known(player, global));
        visits.visit(UUID.randomUUID(), destinationId);
        assertFalse(plugin.known(player, global));
        visits.visit(playerId, destinationId);
        assertTrue(plugin.known(player, global));
    }

    @Test void visitingWorldDoesNotBypassStoneActivationOrPrivacy() {
        config.set("teleport.require-world-discovery", true);
        visits.visit(playerId, destinationId);
        assertFalse(plugin.known(player, target));
        stones.put(playerId, Set.of(target.id()));
        assertTrue(plugin.known(player, target));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        assertFalse(plugin.known(player, target.visibility(true)));
    }

    @Test void unvisitedWorldDestinationsStayListedWithoutBypassingActivationOrPrivacy() {
        config.set("teleport.require-world-discovery", true);
        assertFalse(plugin.listed(player, target));
        stones.put(playerId, Set.of(target.id()));
        assertTrue(plugin.listed(player, target));
        assertFalse(plugin.known(player, target));
        config.set("waystones.public.enabled", true);
        stones.clear();
        assertTrue(plugin.listed(player, target.withPublicAccess(true)));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        assertFalse(plugin.listed(player, target.visibility(true)));
    }

    @Test void currentWorldIsAlreadyDiscoveredAndOtherWorldsRemainLocked() {
        config.set("teleport.require-world-discovery", true);
        assertTrue(plugin.worldDiscovered(player, homeId));
        assertFalse(plugin.worldDiscovered(player, destinationId));
    }

    @Test void worldChangeRecordsDestinationWhileRequirementIsDisabled() {
        plugin.changedWorld(new PlayerChangedWorldEvent(player, mock(World.class)));
        assertTrue(visits.visited(playerId, homeId));
        config.set("teleport.require-world-discovery", true);
        assertTrue(plugin.worldDiscovered(player, homeId));
        assertFalse(plugin.worldDiscovered(player, destinationId));
    }

    @Test void visitHistorySurvivesYamlRoundTripAndIsIndependentOfStones() throws Exception {
        assertTrue(visits.visit(playerId, homeId));
        assertFalse(visits.visit(playerId, homeId));
        visits.visit(playerId, destinationId);
        YamlConfiguration saved = new YamlConfiguration();
        saved.set("stones.unrelated", "preserved");
        visits.save(saved);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(saved.saveToString());
        WorldDiscoveries restored = new WorldDiscoveries();
        restored.load(reloaded);
        assertTrue(restored.visited(playerId, homeId));
        assertTrue(restored.visited(playerId, destinationId));
        assertFalse(restored.visited(UUID.randomUUID(), destinationId));
        assertEquals("preserved", reloaded.getString("stones.unrelated"));
        restored.load(new YamlConfiguration());
        assertFalse(restored.visited(playerId, homeId));
    }
}
