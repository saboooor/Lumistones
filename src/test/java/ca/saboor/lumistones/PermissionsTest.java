package ca.saboor.lumistones;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionsTest {
    private final Lumistones plugin = mock(Lumistones.class, CALLS_REAL_METHODS);
    @Test void explicitDenialOverridesAdministration() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("lumistones.admin")).thenReturn(true);
        when(sender.isPermissionSet("lumistones.use")).thenReturn(true);
        assertFalse(plugin.permission(sender, "lumistones.use"));
    }
    @Test void administrationGrantsManagementButDoesNotGrantFlight() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("lumistones.admin")).thenReturn(true);
        assertTrue(plugin.permission(sender, "lumistones.reload"));
        assertFalse(plugin.permission(sender, "lumistones.flightzone.fly"));
    }
    @Test void directPermissionGrantWorksWithoutAdministration() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("lumistones.place")).thenReturn(true);
        assertTrue(plugin.permission(sender, "lumistones.place"));
        assertFalse(plugin.permission(sender, "lumistones.reload"));
    }
}
