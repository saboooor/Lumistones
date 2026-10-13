package ca.saboor.lumistones;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StoneDataTest {
    @Test void relocationPreservesIdentitySettingsAndDiscoverers() {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID(), world = UUID.randomUUID();
        Stone original = new Stone(id, owner, world, 1, 64, 2, "Home: upstairs", false,
                Material.DIAMOND_BLOCK, 5, true, true, "DIAMOND");
        Set<UUID> players = Set.of(owner, UUID.randomUUID());
        UUID destination = UUID.randomUUID();
        StoneData restored = StoneData.decode(new StoneData(original, players).encode(), destination, 8, 90, 12);
        assertEquals(new Stone(id, owner, destination, 8, 90, 12, original.name(), false,
                Material.DIAMOND_BLOCK, 5, true, true, "DIAMOND"), restored.stone());
        assertEquals(players, restored.discoverers());
        Stone privateStone = original.withPublicAccess(false).visibility(true);
        assertEquals(privateStone, StoneData.decode(new StoneData(privateStone, players).encode(), world, 1, 64, 2).stone());
    }
    @Test void corruptMetadataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> StoneData.decode("id: bad", UUID.randomUUID(), 0, 0, 0));
    }
    @Test void privateConfirmationWarnsAboutGlobalStatusAndRefunds() {
        UUID id = UUID.randomUUID();
        var view = Dialogs.privateConfirmation(new Stone(id, id, id, 0, 0, 0, "Home", false).withPublicAccess(true), "token");
        String message = view.getBase().body().getFirst().contents().toPlainText();
        assertTrue(message.contains("removes its global status"));
        assertTrue(message.contains("will not be refunded"));
        assertEquals("/lumistones edit token confirm-private", view.actions().getFirst().action().command());
        assertEquals("/lumistones edit token back", view.exit().action().command());
    }
    @Test void localConfirmationWarnsAndOffersCancel() {
        UUID id = UUID.randomUUID();
        var view = Dialogs.localConfirmation(new Stone(id, id, id, 0, 0, 0, "Home", false).withPublicAccess(true), "token");
        assertTrue(view.getBase().body().getFirst().contents().toPlainText().contains("will not be refunded"));
        assertEquals("/lumistones edit token confirm-local", view.actions().getFirst().action().command());
        assertEquals("/lumistones edit token back", view.exit().action().command());
    }
}
