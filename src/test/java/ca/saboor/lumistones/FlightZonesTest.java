package ca.saboor.lumistones;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlightZonesTest {
    @TempDir Path directory;
    private Lumistones plugin;
    private FlightZones zones;
    private World world;
    private Player player;
    @BeforeEach void setup() throws Exception {
        plugin = mock(Lumistones.class); when(plugin.getName()).thenReturn("Lumistones"); when(plugin.namespace()).thenReturn("lumistones"); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var config = new YamlConfiguration(); config.set("flight-zone.announce", false); config.set("flight-zone.fall-damage-grace-seconds", 5); when(plugin.getConfig()).thenReturn(config);
        zones = new FlightZones(plugin); world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        var zone = new FlightZones.FlightZone("plot", "Build Plot", UUID.randomUUID(), "Owner", new Lumistones.BlockKey(world.getUID(), 0, 64, 0), 4, 5);
        this.<Map<String, FlightZones.FlightZone>>field(zones, "zones").put("plot", zone);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64, 0.5)); when(plugin.permission(player, FlightZones.PERMISSION_FLY)).thenReturn(true);
    }
    @Test void grantsAndRevokesOnlyFlightItOwns() throws Exception {
        update(zones); verify(player).setAllowFlight(true);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0)); update(zones); verify(player).setAllowFlight(false);
        assertTrue(this.<Map<UUID, String>>field(zones, "granted").isEmpty());
    }
    @Test void externalFlightIsNeverTakenOverOrRevoked() throws Exception {
        when(player.getAllowFlight()).thenReturn(true); update(zones);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0)); update(zones);
        verify(player, never()).setAllowFlight(anyBoolean()); assertTrue(this.<Map<UUID, String>>field(zones, "granted").isEmpty());
    }
    @Test void creativeAndSpectatorFlightSurvivePermissionLoss() throws Exception {
        for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
            this.<Map<UUID, String>>field(zones, "granted").put(player.getUniqueId(), "plot"); when(player.getGameMode()).thenReturn(mode); update(zones);
        }
        verify(player, never()).setAllowFlight(false); verify(player, never()).setFlying(false);
    }
    @Test void revocationGivesFallDamageGraceOnce() throws Exception {
        update(zones); when(player.isFlying()).thenReturn(true); when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0)); update(zones);
        var fall = fallEvent(); zones.onFallDamage(fall); verify(fall).setCancelled(true);
        var nextFall = fallEvent(); zones.onFallDamage(nextFall); verify(nextFall, never()).setCancelled(true);
    }
    @Test void persistedGrantIsRecoveredAndRevokedOutsideZone() throws Exception {
        update(zones); var loaded = FlightZones.class.getDeclaredField("loaded"); loaded.setAccessible(true); loaded.set(zones, true);
        invoke(zones, "save");
        FlightZones recovered = new FlightZones(plugin); invoke(recovered, "load");
        assertTrue(this.<Set<UUID>>field(recovered, "pendingRevoke").contains(player.getUniqueId()));
        when(player.getAllowFlight()).thenReturn(true); when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0)); update(recovered);
        verify(player).setAllowFlight(false); assertTrue(this.<Set<UUID>>field(recovered, "pendingRevoke").isEmpty());
    }
    @Test void widthAndHeightUseHalfOpenBoundsAndMatchWorld() throws Exception {
        var zone = this.<Map<String, FlightZones.FlightZone>>field(zones, "zones").get("plot");
        assertTrue(zone.contains(new Location(world, -1, 63, -1))); assertTrue(zone.contains(new Location(world, 2.99, 67.99, 2.99)));
        assertFalse(zone.contains(new Location(world, 3, 64, 0))); assertFalse(zone.contains(new Location(world, 0, 68, 0))); assertFalse(zone.contains(new Location(world, 0, 62.99, 0)));
        World other = mock(World.class); when(other.getUID()).thenReturn(UUID.randomUUID()); assertFalse(zone.contains(new Location(other, 0, 64, 0)));
    }
    private EntityDamageEvent fallEvent() { EntityDamageEvent event = mock(EntityDamageEvent.class); when(event.getEntity()).thenReturn(player); when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL); return event; }
    private void update(FlightZones target) throws Exception { var method = FlightZones.class.getDeclaredMethod("updatePlayer", Player.class); method.setAccessible(true); method.invoke(target, player); }
    private void invoke(FlightZones target, String name) throws Exception { var method = FlightZones.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(target); }
    @SuppressWarnings("unchecked") private <T> T field(FlightZones target, String name) throws Exception { Field field = FlightZones.class.getDeclaredField(name); field.setAccessible(true); return (T)field.get(target); }
}
