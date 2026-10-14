package ca.saboor.lumistones;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BodyInteractionTest {
    private Lumistones plugin;
    private Player player;
    private Stone stone;
    private StoneModel model;
    private ItemStack held;
    @BeforeEach void setup() throws Exception {
        plugin = mock(Lumistones.class, CALLS_REAL_METHODS);
        player = mock(Player.class);
        var owner = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(owner);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        stone = new Stone(UUID.randomUUID(), owner, UUID.randomUUID(), 0, 64, 0, "Home", false).withIcon("DIAMOND");
        model = mock(StoneModel.class);
        set("model", model);
        set("stones", new LinkedHashMap<>(Map.of(stone.id(), stone)));
        set("discoveries", new HashMap<UUID, Set<UUID>>());
        doNothing().when(plugin).activate(any(), any());
        doReturn("Message").when(plugin).info(anyString());
        held = mock(ItemStack.class);
        when(held.getType()).thenReturn(Material.BRICKS);
        when(held.getAmount()).thenReturn(5);
    }
    private void set(String name, Object value) throws Exception {
        var field = Lumistones.class.getDeclaredField(name); field.setAccessible(true); field.set(plugin, value);
    }
    @Test void ownerChangesMaterialInPlaceAndConsumesOneBlock() {
        plugin.changeBody(player, stone, held);
        Stone updated = plugin.stones.get(stone.id());
        assertEquals(stone.material(Material.BRICKS), updated);
        verify(held).setAmount(4);
        verify(model).remove(stone); verify(model).spawn(updated);
    }
    @Test void creativeChangeDoesNotConsumeBlock() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        plugin.changeBody(player, stone, held);
        assertEquals(Material.BRICKS, plugin.stones.get(stone.id()).bodyBlock());
        verify(held, never()).setAmount(anyInt());
    }
    @Test void nonOwnerCannotChangeMaterialOrLoseItems() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        plugin.changeBody(player, stone, held);
        assertEquals(stone, plugin.stones.get(stone.id()));
        verifyNoInteractions(model); verify(held, never()).setAmount(anyInt());
    }
    @Test void currentMaterialDoesNotConsumeBlockOrRespawn() {
        when(held.getType()).thenReturn(stone.bodyBlock());
        plugin.changeBody(player, stone, held);
        verifyNoInteractions(model); verify(held, never()).setAmount(anyInt());
    }
    @Test void administratorCanChangeMaterialWithoutOwnership() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        doReturn(true).when(plugin).permission(player, "lumistones.admin");
        plugin.changeBody(player, stone, held);
        assertEquals(Material.BRICKS, plugin.stones.get(stone.id()).bodyBlock());
        verify(held).setAmount(4);
        verify(model).spawn(any(Stone.class));
    }
    @Test void shiftRightClickWithBlockTriggersChangeBodyInClick() throws Exception {
        when(player.isSneaking()).thenReturn(true);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(held);
        doReturn(true).when(plugin).worldEnabled(any());
        doReturn(true).when(plugin).permission(player, "lumistones.use");
        doReturn(true).when(plugin).near(player, stone);
        plugin.click(player, stone);
        assertEquals(Material.BRICKS, plugin.stones.get(stone.id()).bodyBlock());
        verify(held).setAmount(4);
    }
    @Test void regularRightClickWithBlockDoesNotChangeBody() throws Exception {
        when(player.isSneaking()).thenReturn(false);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(held);
        doReturn(true).when(plugin).worldEnabled(any());
        doReturn(true).when(plugin).permission(player, "lumistones.use");
        doReturn(true).when(plugin).near(player, stone);
        var dialogs = mock(Dialogs.class);
        set("dialogs", dialogs);
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        doReturn(config).when(plugin).getConfig();
        plugin.click(player, stone);
        assertEquals(Material.POLISHED_ANDESITE, plugin.stones.get(stone.id()).bodyBlock());
        verify(held, never()).setAmount(anyInt());
        verify(dialogs).destinations(player, stone, false);
    }
    @Test void protectedClickDiscoversWithoutEditingOrOpeningManagement() throws Exception {
        when(player.isSneaking()).thenReturn(true);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(held);
        doReturn(true).when(plugin).worldEnabled(any());
        doReturn(true).when(plugin).permission(player, "lumistones.use");
        doReturn(true).when(plugin).near(player, stone);
        var dialogs = mock(Dialogs.class);
        set("dialogs", dialogs);
        plugin.click(player, stone, false);
        verify(plugin).activate(player, stone);
        verify(dialogs).destinations(player, stone, false);
        verify(dialogs, never()).manage(any(), any());
        verify(held, never()).setAmount(anyInt());
        verifyNoInteractions(model);
        assertEquals(stone, plugin.stones.get(stone.id()));
    }
    @Test void shiftRightClickWithAirOpensManageInsteadOfChangingBody() throws Exception {
        when(player.isSneaking()).thenReturn(true);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        var air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.getAmount()).thenReturn(0);
        when(inventory.getItemInMainHand()).thenReturn(air);
        doReturn(true).when(plugin).worldEnabled(any());
        doReturn(true).when(plugin).permission(player, "lumistones.use");
        doReturn(true).when(plugin).near(player, stone);
        var dialogs = mock(Dialogs.class);
        set("dialogs", dialogs);
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        doReturn(config).when(plugin).getConfig();
        plugin.click(player, stone);
        assertEquals(Material.POLISHED_ANDESITE, plugin.stones.get(stone.id()).bodyBlock());
        verify(dialogs).manage(player, stone);
    }
}
