package ca.saboor.lumistones;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.event.ClickEvent;
import java.util.*;

/** Shared menu description for Paper dialogs and Floodgate forms. */
final class DialogView {
    record TextComponent(String toPlainText) {}
    record PlainMessageBody(TextComponent contents, int width) {}
    record Action(String command, boolean dynamic) {}
    enum Icon {
        TRAVEL("item/ender_pearl"), SAVE("item/feather"), RETRIEVE("item/iron_pickaxe"),
        SHARED("item/emerald"), PRIVATE("item/iron_door"), CLOSE("item/barrier"), INFO("item/paper");
        final String sprite;
        Icon(String sprite) { this.sprite = sprite; }
    }
    record ActionButton(TextComponent label, Action action, int width, Icon icon) {}
    record MultiActionDialog(DialogBase getBase, List<ActionButton> actions, int columns, ActionButton exit) {}
    static final class DialogBase {
        private final TextComponent title;
        private List<PlainMessageBody> body = List.of();
        private List<Input> inputs = List.of();
        private boolean canCloseWithEscape = true;
        DialogBase(TextComponent title) { this.title = title; }
        DialogBase body(List<PlainMessageBody> value) { body = List.copyOf(value); return this; }
        DialogBase inputs(List<Input> value) { inputs = List.copyOf(value); return this; }
        DialogBase canCloseWithEscape(boolean value) { canCloseWithEscape = value; return this; }
        boolean canCloseWithEscape() { return canCloseWithEscape; }
        TextComponent title() { return title; }
        List<PlainMessageBody> body() { return body; }
        List<Input> inputs() { return inputs; }
    }
    interface Input { String key(); }
    static final class TextInput implements Input {
        private final String key; private final TextComponent label;
        private String initial = ""; private int maxLength = 32;
        TextInput(String key, TextComponent label) { this.key = key; this.label = label; }
        public String key() { return key; } TextComponent label() { return label; }
        String initial() { return initial; } int maxLength() { return maxLength; }
        TextInput initial(String value) { initial = value; return this; }
        TextInput maxLength(int value) { maxLength = value; return this; }
    }
    static final class NumberRangeInput implements Input {
        private final String key; private final TextComponent label; private final float start, end;
        private Float initial, step;
        NumberRangeInput(String key, TextComponent label, float start, float end) { this.key = key; this.label = label; this.start = start; this.end = end; }
        public String key() { return key; } TextComponent label() { return label; }
        float start() { return start; } float end() { return end; } Float initial() { return initial; } Float step() { return step; }
        NumberRangeInput initial(Float value) { initial = value; return this; }
        NumberRangeInput step(Float value) { step = value; return this; }
    }
    static final class BooleanInput implements Input {
        private final String key; private final TextComponent label; private boolean initial;
        BooleanInput(String key, TextComponent label) { this.key = key; this.label = label; }
        public String key() { return key; } TextComponent label() { return label; } boolean initial() { return initial; }
        BooleanInput initial(boolean value) { initial = value; return this; }
    }
    static Dialog paper(MultiActionDialog view) {
        return Dialog.create(factory -> factory.empty().base(paperBase(view)).type(DialogType.multiAction(
                view.actions().stream().map(DialogView::button).toList(), button(view.exit()), view.columns())));
    }
    static io.papermc.paper.registry.data.dialog.DialogBase paperBase(MultiActionDialog view) {
        return io.papermc.paper.registry.data.dialog.DialogBase.builder(Component.text(view.getBase().title().toPlainText()))
                .pause(false).canCloseWithEscape(view.getBase().canCloseWithEscape()).body(view.getBase().body().stream().map(body -> DialogBody.plainMessage(Component.text(body.contents().toPlainText()), body.width())).toList())
                .inputs(view.getBase().inputs().stream().map(DialogView::input).toList()).build();
    }
    private static DialogInput input(Input value) {
        if (value instanceof TextInput text) return DialogInput.text(text.key(), Component.text(text.label().toPlainText())).initial(text.initial()).maxLength(text.maxLength()).build();
        if (value instanceof NumberRangeInput number) return DialogInput.numberRange(number.key(), Component.text(number.label().toPlainText()), number.start(), number.end()).initial(number.initial()).step(number.step()).build();
        if (value instanceof BooleanInput bool) return DialogInput.bool(bool.key(), Component.text(bool.label().toPlainText()), bool.initial(), "true", "false");
        throw new IllegalArgumentException("Unsupported dialog input");
    }
    static Component buttonLabel(ActionButton value) {
        Component text = Component.text(value.label().toPlainText());
        if (value.icon() == null) return text;
        return Component.object(ObjectContents.sprite(Key.key("minecraft:items"), Key.key("minecraft:" + value.icon().sprite)))
                .append(Component.text(" ")).append(text);
    }
    static io.papermc.paper.registry.data.dialog.ActionButton button(ActionButton value) {
        var action = value.action().dynamic() ? DialogAction.commandTemplate(value.action().command()) : DialogAction.staticAction(ClickEvent.runCommand(value.action().command()));
        return io.papermc.paper.registry.data.dialog.ActionButton.create(buttonLabel(value), null, value.width(), action);
    }
}
