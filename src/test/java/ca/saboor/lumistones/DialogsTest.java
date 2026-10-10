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
        assertEquals(Key.key("minecraft:items"), sprite.atlas());
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
