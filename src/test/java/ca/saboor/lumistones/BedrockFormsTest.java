package ca.saboor.lumistones;
import ca.saboor.lumistones.DialogView.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.geysermc.cumulus.form.*;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedrockFormsTest {
    /** Only the Floodgate transport is mocked; the Cumulus builders and response codecs are real. */
    public interface Transport { boolean sendForm(UUID player, FormBuilder<?, ?, ?> builder); boolean isFloodgatePlayer(UUID player); }
    private Lumistones plugin;
    private Player player;
    private BedrockForms bridge;
    private final AtomicReference<Form> sent = new AtomicReference<>();
    @BeforeEach void setup() throws Exception {
        plugin = mock(Lumistones.class); when(plugin.isEnabled()).thenReturn(true); when(plugin.getLogger()).thenReturn(Logger.getLogger("BedrockFormsTest"));
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
        Transport transport = mock(Transport.class); when(transport.isFloodgatePlayer(any())).thenReturn(true);
        when(transport.sendForm(any(), any())).thenAnswer(call -> { sent.set(((FormBuilder<?, ?, ?>)call.getArgument(1)).build()); return true; });
        bridge = new BedrockForms(plugin);
        set("loader", getClass().getClassLoader()); set("apiClass", Transport.class); set("api", transport);
    }
    @Test void simpleSelectionSchedulesServerThreadCommand() throws Exception {
        assertTrue(bridge.isBedrock(player));
        bridge.show(player, Dialogs.menu(Dialogs.base("Waystones", "Travel"), List.of(Dialogs.button("Spawn", "/lumistones choose token target", false))));
        assertInstanceOf(SimpleForm.class, sent.get()); assertEquals("Spawn", ((SimpleForm)sent.get()).buttons().getFirst().text());
        assertScheduled("0", "/lumistones choose token target");
    }
    @Test void realCustomResponsePreservesInputsAndActionChoice() throws Exception {
        var base = Dialogs.base("Manage", "Edit").inputs(List.of(
            new TextInput("name", new TextComponent("Name")).initial("Old"),
            new NumberRangeInput("width", new TextComponent("Width"), 1, 256).initial(32f).step(1f),
            new BooleanInput("public", new TextComponent("Public")).initial(false)
        ));
        bridge.show(player, Dialogs.menu(base, List.of(Dialogs.button("Save", "/lumistones test $(width) $(public) $(name)", true))));
        assertInstanceOf(CustomForm.class, sent.get()); assertEquals(4, ((CustomForm)sent.get()).content().size());
        assertScheduled("[\"New Home /@\",32,true,0]", "/lumistones test 32.0 true New Home");
    }
    @Test void closingAndInvalidResponsesCannotExecuteActions() throws Exception {
        bridge.show(player, Dialogs.menu(Dialogs.base("Waystones", "Travel"), List.of(Dialogs.button("Spawn", "/lumistones choose token target", false))));
        FormDefinitions.instance().definitionFor(sent.get()).handleFormResponse(sent.get(), "null");
        FormDefinitions.instance().definitionFor(sent.get()).handleFormResponse(sent.get(), "100");
        verify(plugin, never()).executeUiCommand(any(), any());
    }
    private void assertScheduled(String response, String command) throws Exception {
        AtomicReference<Runnable> queued = new AtomicReference<>(); BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { queued.set(call.getArgument(1)); return null; });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            FormDefinitions.instance().definitionFor(sent.get()).handleFormResponse(sent.get(), response);
            verify(plugin, never()).executeUiCommand(any(), any()); assertNotNull(queued.get()); queued.get().run();
            verify(plugin).executeUiCommand(player, command);
        }
    }
    private void set(String name, Object value) throws Exception { var field = BedrockForms.class.getDeclaredField(name); field.setAccessible(true); field.set(bridge, value); }
}
