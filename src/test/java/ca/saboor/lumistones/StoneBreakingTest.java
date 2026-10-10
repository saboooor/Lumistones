package ca.saboor.lumistones;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoneBreakingTest {
    private Lumistones plugin;
    private Block block;
    private World world;
    private Player player;
    private Stone stone;
    @BeforeEach void setup() throws Exception {
        plugin = mock(Lumistones.class, CALLS_REAL_METHODS);
        world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        block = mock(Block.class); when(block.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        stone = new Stone(UUID.randomUUID(), player.getUniqueId(), world.getUID(), 0, 64, 0, "Home", false);
        set("stones", new LinkedHashMap<>(Map.of(stone.id(), stone)));
        set("retrieving", new HashSet<UUID>());
        doReturn("Denied").when(plugin).info(anyString());
    }
    private void set(String name, Object value) throws Exception {
        var field = Lumistones.class.getDeclaredField(name); field.setAccessible(true); field.set(plugin, value);
    }
    @Test void topAnchorCanBeBrokenAndProtectsOwnership() {
        when(block.getLocation()).thenReturn(new Location(world, 0, 65, 0));
        var event = new BlockBreakEvent(block, player); plugin.breakStone(event);
        assertFalse(event.isCancelled()); assertFalse(event.isDropItems());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var denied = new BlockBreakEvent(block, player); plugin.breakStone(denied);
        assertTrue(denied.isCancelled());
    }
    @Test void spaceAboveTheAnchorStackIsNotAMiningTarget() {
        when(block.getLocation()).thenReturn(new Location(world, 0, 66, 0));
        var event = new BlockBreakEvent(block, player); plugin.breakStone(event);
        assertFalse(event.isCancelled()); assertTrue(event.isDropItems());
    }
    @Test void ownerBreakSuppressesVanillaWallDropAndExperience() {
        var event = new BlockBreakEvent(block, player); event.setExpToDrop(5);
        plugin.breakStone(event);
        assertFalse(event.isCancelled()); assertFalse(event.isDropItems()); assertEquals(0, event.getExpToDrop());
    }
    @Test void anotherPlayerCannotBreakTheStone() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var event = new BlockBreakEvent(block, player);
        plugin.breakStone(event);
        assertTrue(event.isCancelled());
    }
    @Test void protectionCancellationAfterMonitorDoesNotRemoveOrDropStone() {
        var server = mock(Server.class); var scheduler = mock(BukkitScheduler.class);
        doReturn(server).when(plugin).getServer(); when(server.getScheduler()).thenReturn(scheduler);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { queued.set(call.getArgument(1)); return null; });
        var event = new BlockBreakEvent(block, player);
        plugin.breakStone(event); plugin.brokenStone(event);
        assertNotNull(queued.get());
        event.setCancelled(true); queued.get().run();
        verify(plugin, never()).remove(any());
        verify(block, never()).getWorld();
    }
}
