package ca.saboor.lumistones;

import ca.saboor.lumistones.DialogView.*;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.DialogBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.key.Key;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DialogsTest {
    @Test void freeDestinationsAndCurrentWorldHaveNoExtraDetails() {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        var home = Dialogs.destinationLabel(config, "Home", "world", Cost.FREE, true);
        assertEquals("Home", home.toPlainText());
        assertNull(home.xpLevels());
        assertEquals("Home", BedrockForms.buttonLabel(new ActionButton(home, null, 300, null)));
        var away = Dialogs.destinationLabel(config, "Home", "world_nether", Cost.FREE, false);
        assertEquals("Home · Nether", away.toPlainText());
        assertNull(away.xpLevels());
        var paid = Dialogs.destinationLabel(config, "Home", "world", new Cost(3, 0), true);
        assertEquals("Home · 3 XP", BedrockForms.buttonLabel(new ActionButton(paid, null, 300, null)));
        var money = Dialogs.destinationLabel(config, "Home", "world", new Cost(0, 5), true);
        assertEquals("Home · 5.0 money", BedrockForms.buttonLabel(new ActionButton(money, null, 300, null)));
    }
    @Test void everyManagementButtonSubmitsEditedNameAndPreservesLore() {
        var view = Dialogs.editingMenu(Dialogs.base("Manage", "Edit"),
                List.of(Dialogs.button("Travel", "/lumistones edit token travel", false),
                        Dialogs.button("Private", "/lumistones edit token private", false),
                        Dialogs.button("Icon", "/lumistones edit token icon", false)), "/lumistones edit token ");
        for (var button : view.actions()) {
            assertTrue(button.action().dynamic());
            assertTrue(BedrockForms.command(button.action(), java.util.Map.of("name", "New home")).endsWith(" New home"));
            assertFalse(button.lore().isBlank());
        }
    }
    @Test void selectedIconSurvivesOtherEdits() {
        var id = java.util.UUID.randomUUID();
        var stone = new Stone(id, id, id, 0, 0, 0, "Home", false).withIcon("DIAMOND");
        assertEquals(Icon.DIAMOND, stone.named("New home").visibility(true).withOwnerHead(true).withPublicAccess(true).material(org.bukkit.Material.STONE).icon());
        assertEquals(Icon.TRAVEL, stone.withIcon("unknown").icon());
    }
    @Test void travelButtonShowsOwnerHeadAndReadableBedrockOwner() {
        var owner = java.util.UUID.randomUUID();
        var button = new ActionButton(new TextComponent("Home"), null, 300, Icon.DIAMOND, "Owner: Alex", owner, "Alex");
        var label = DialogView.buttonLabel(button);
        var text = label.children().get(1);
        var head = assertInstanceOf(ObjectComponent.class, text.children().get(1));
        assertEquals(owner, assertInstanceOf(net.kyori.adventure.text.object.PlayerHeadObjectContents.class, head.contents()).id());
        assertEquals("Owner: Alex", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize((Component) head.hoverEvent().value()));
        assertEquals("Home · Owner: Alex", BedrockForms.buttonLabel(button));
    }
    @Test void iconPickerUsesCompactGridAndIconOnlyLabels() {
        var id = java.util.UUID.randomUUID();
        var view = Dialogs.iconMenu(new Stone(id, id, id, 0, 0, 0, "Home", false), "token");
        assertEquals(14, view.columns());
        assertEquals(Icon.values().length, view.actions().size());
        for (var button : view.actions()) {
            assertEquals(20, button.width());
            assertEquals("", button.label().toPlainText());
            assertTrue(DialogView.buttonLabel(button).children().isEmpty());
            assertFalse(BedrockForms.buttonLabel(button).isBlank());
            assertTrue(button.lore().contains(button.icon().name().replace('_', ' ')));
        }
    }
    @Test void spriteAtlasMatchesMinecraftVersion() {
        assertEquals(Key.key("minecraft:blocks"), DialogView.spriteAtlas("1.21.10"));
        assertEquals(Key.key("minecraft:items"), DialogView.spriteAtlas("1.21.11"));
        assertEquals(Key.key("minecraft:items"), DialogView.spriteAtlas("26.1.2"));
    }
    @Test void enabledWorldsRequireExplicitEntriesIncludingDottedNames() throws Exception {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        assertFalse(Lumistones.worldEnabled(config, "world"));
        config.loadFromString("enabled-worlds:\n  world:\n    name: Overworld\n  custom.world:\n    name: Moon\n");
        assertTrue(Lumistones.worldEnabled(config, "world"));
        assertTrue(Lumistones.worldEnabled(config, "custom.world"));
        assertFalse(Lumistones.worldEnabled(config, "world_nether"));
        config.set("enabled-worlds.world", null);
        assertFalse(Lumistones.worldEnabled(config, "world"));
    }

    @Test void worldIconAppearsBesideAliasAndSupportsConfiguredSprites() throws Exception {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.loadFromString("enabled-worlds:\n  custom.world:\n    name: Moon\n    sprite: minecraft:item/diamond\n");
        var label = Dialogs.destinationLabel(config, "Home", "custom.world", 3);
        assertEquals("Home · Moon · ", label.toPlainText());
        var sprite = assertInstanceOf(SpriteObjectContents.class,
                assertInstanceOf(ObjectComponent.class, label.paperText().children().getFirst()).contents());
        assertEquals(Key.key("minecraft:item/diamond"), sprite.sprite());
        assertEquals("Moon · ", assertInstanceOf(net.kyori.adventure.text.TextComponent.class,
                label.paperText().children().get(2)).content());
        assertEquals("Home · Moon · 3 XP", BedrockForms.buttonLabel(new ActionButton(label, null, 300, null)));
        config.set("enabled-worlds.world.sprite", "");
        assertNull(Dialogs.worldSprite(config, "world"));
        config.set("enabled-worlds.world.sprite", "invalid sprite");
        assertNull(Dialogs.worldSprite(config, "world"));
        assertEquals("minecraft:item/nether_star", Dialogs.worldSprite(config, "world_nether"));
    }

    @Test void destinationCostUsesTextureWithReadableBedrockFallback() {
        var button = new ActionButton(new TextComponent("Home · Overworld · ", 3),
                new Action("/lumistones choose token destination", false), 300, null);
        var label = DialogView.buttonLabel(button);
        var icon = assertInstanceOf(ObjectComponent.class, label.children().getFirst());
        var sprite = assertInstanceOf(SpriteObjectContents.class, icon.contents());
        assertEquals(Key.key("minecraft:blocks"), sprite.atlas());
        assertEquals(Key.key("minecraft:item/experience_bottle"), sprite.sprite());
        assertEquals(" 3", assertInstanceOf(net.kyori.adventure.text.TextComponent.class, label.children().get(1)).content());
        assertEquals("Home · Overworld · 3 XP", BedrockForms.buttonLabel(button));
    }

    @Test void worldAliasesSupportDefaultsOverridesAndLiteralWorldNames() throws Exception {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        assertEquals("Overworld", Dialogs.worldAlias(config, "world"));
        assertEquals("Nether", Dialogs.worldAlias(config, "world_nether"));
        assertEquals("The End", Dialogs.worldAlias(config, "world_the_end"));
        assertEquals("custom", Dialogs.worldAlias(config, "custom"));
        var aliases = config.createSection("enabled-worlds");
        aliases.set("world", "Home");
        assertEquals("Home", Dialogs.worldAlias(config, "world"));
        config.loadFromString("enabled-worlds:\n  custom.world: Moon\n");
        assertEquals("Moon", Dialogs.worldAlias(config, "custom.world"));
    }

    @Test void closeSubmitsNameButEscapeDismissalRemainsEnabled() {
        var view = Dialogs.editingMenu(Dialogs.base("Manage", "Close saves the name"), List.of(Dialogs.button("Travel", "/lumistones edit token travel", false), Dialogs.button("Make private", "/lumistones edit token private", false)), "/lumistones edit token ");
        assertEquals(2, view.columns());
        assertEquals(150, view.actions().getFirst().width());
        assertEquals(2, view.actions().size());
        assertTrue(view.getBase().canCloseWithEscape());
        assertTrue(view.exit().action().dynamic());
        assertEquals("/lumistones edit token rename $(name)", view.exit().action().command());
        assertEquals("/lumistones edit token rename My home", BedrockForms.command(view.exit().action(), java.util.Map.of("name", "My home")));
    }

    @Test void buttonIconUsesVanillaAtlasAndPreservesLiteralLabel() {
        String name = "Home <sprite:items:item/diamond>";
        var button = Dialogs.button(name, "/lumistones choose token destination", false, Icon.TRAVEL);
        var label = assertInstanceOf(ObjectComponent.class, DialogView.buttonLabel(button));
        var sprite = assertInstanceOf(SpriteObjectContents.class, label.contents());
        assertEquals(Key.key("minecraft:blocks"), sprite.atlas());
        assertEquals(Key.key("minecraft:item/ender_pearl"), sprite.sprite());
        assertEquals(" ", assertInstanceOf(net.kyori.adventure.text.TextComponent.class, label.children().get(0)).content());
        assertEquals(name, assertInstanceOf(net.kyori.adventure.text.TextComponent.class, label.children().get(1)).content());
        assertEquals(name, button.label().toPlainText()); // Floodgate retains readable text.
    }
    @Test void destinationMenuUsesPaperBodyAndCommandAction() {
        var provider = mock(DialogInstancesProvider.class);
        var baseBuilder = mock(DialogBase.Builder.class, RETURNS_SELF);
        var buttonBuilder = mock(io.papermc.paper.registry.data.dialog.ActionButton.Builder.class, RETURNS_SELF);
        when(provider.dialogBaseBuilder(any())).thenReturn(baseBuilder);
        when(provider.actionButtonBuilder(any())).thenReturn(buttonBuilder);
        try (var factory = mockStatic(DialogInstancesProvider.class)) {
            factory.when(DialogInstancesProvider::instance).thenReturn(provider);
            var view = Dialogs.menu(Dialogs.base("A quoted stone", "Pick a destination"), List.of(Dialogs.button("Spawn", "/lumistones choose token destination", false)));
            DialogView.paperBase(view);
            DialogView.button(view.actions().getFirst());
            verify(provider).dialogBaseBuilder(Component.text("A quoted stone"));
            verify(provider).plainMessageDialogBody(Component.text("Pick a destination"), 350);
            verify(baseBuilder).pause(false);
            verify(provider).staticAction(ClickEvent.runCommand("/lumistones choose token destination"));
            verify(buttonBuilder).width(300);
        }
    }
    @Test void namingMenuPreservesPaperTextInputAndCommandTemplate() {
        var provider = mock(DialogInstancesProvider.class);
        var baseBuilder = mock(DialogBase.Builder.class, RETURNS_SELF);
        var textBuilder = mock(io.papermc.paper.registry.data.dialog.input.TextDialogInput.Builder.class, RETURNS_SELF);
        var buttonBuilder = mock(io.papermc.paper.registry.data.dialog.ActionButton.Builder.class, RETURNS_SELF);
        when(provider.dialogBaseBuilder(any())).thenReturn(baseBuilder);
        when(provider.textBuilder(any(), any())).thenReturn(textBuilder);
        when(provider.actionButtonBuilder(any())).thenReturn(buttonBuilder);
        try (var factory = mockStatic(DialogInstancesProvider.class)) {
            factory.when(DialogInstancesProvider::instance).thenReturn(provider);
            var base = Dialogs.base("Name stone", "Rename").inputs(List.of(new TextInput("name", new TextComponent("Name")).initial("Home").maxLength(32)));
            var view = Dialogs.menu(base, List.of(Dialogs.button("Save", "/lumistones edit token rename $(name)", true)));
            DialogView.paperBase(view);
            DialogView.button(view.actions().getFirst());
            verify(provider).textBuilder("name", Component.text("Name"));
            verify(textBuilder).initial("Home");
            verify(textBuilder).maxLength(32);
            verify(provider).commandTemplate("/lumistones edit token rename $(name)");
        }
    }
}
