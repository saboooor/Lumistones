package ca.saboor.lumistones;

import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;

class OverlayVisibilityTest {
    @Test void unactivatedPlayerCannotSeeOverlay() {
        var plugin = mock(Lumistones.class); var player = mock(Player.class); var overlay = mock(TextDisplay.class);
        var stone = new Stone(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0, "Hidden", false);
        StoneModel.updateVisibility(plugin, player, stone, overlay);
        verify(player).hideEntity(plugin, overlay); verify(player, never()).showEntity(any(), any());
    }
    @Test void activationShowsOverlayAndRevokedAccessHidesItAgain() {
        var plugin = mock(Lumistones.class); var player = mock(Player.class); var overlay = mock(TextDisplay.class);
        var stone = new Stone(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0, "Home", false);
        when(plugin.known(player, stone)).thenReturn(true, false);
        StoneModel.updateVisibility(plugin, player, stone, overlay);
        StoneModel.updateVisibility(plugin, player, stone, overlay);
        var order = inOrder(player); order.verify(player).showEntity(plugin, overlay); order.verify(player).hideEntity(plugin, overlay);
    }
}
