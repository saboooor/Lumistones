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
    record TextComponent(String toPlainText, Integer xpLevels, Component paperText, TextComponent afterOwner, Component beforeIcon) {
        TextComponent(String text, Integer xpLevels, Component paperText, TextComponent afterOwner) { this(text, xpLevels, paperText, afterOwner, null); }
        TextComponent(String text, Integer xpLevels, Component paperText) { this(text, xpLevels, paperText, null); }
        TextComponent(String text) { this(text, null, null); }
        TextComponent(String text, Integer xpLevels) { this(text, xpLevels, null); }
    }
    record PlainMessageBody(TextComponent contents, int width) {}
    record Action(String command, boolean dynamic) {}
    enum Icon {
        OWNER("item/name_tag"), TRAVEL("item/ender_pearl"), SAVE("item/feather"), RETRIEVE("item/iron_pickaxe"),
        SHARED("item/emerald"), PRIVATE("item/iron_door"), CLOSE("item/barrier"), INFO("item/paper"), DIAMOND("item/diamond"), HOME("item/oak_door"), NATURE("block/oak_sapling"), STAR("item/nether_star"),
        COMPASS("item/compass_16"), MAP("item/map"), SPYGLASS("item/spyglass"),
        ENDER_EYE("item/ender_eye"), ELYTRA("item/elytra"), FIREWORK("item/firework_rocket"),
        IRON("item/iron_ingot"), GOLD("item/gold_ingot"), COPPER("item/copper_ingot"),
        NETHERITE("item/netherite_ingot"), AMETHYST("item/amethyst_shard"), QUARTZ("item/quartz"),
        COAL("item/coal"), REDSTONE("item/redstone"), LAPIS("item/lapis_lazuli"),
        SWORD("item/diamond_sword"), AXE("item/diamond_axe"), BOW("item/bow"),
        TRIDENT("item/trident"), HELMET("item/diamond_helmet"),
        APPLE("item/apple"), GOLDEN_APPLE("item/golden_apple"), BREAD("item/bread"),
        CARROT("item/carrot"), WHEAT("item/wheat"), FISHING_ROD("item/fishing_rod"),
        BUCKET("item/bucket"), WATER_BUCKET("item/water_bucket"), LAVA_BUCKET("item/lava_bucket"),
        BOOK("item/book"), ENCHANTED_BOOK("item/enchanted_book"), BLAZE_ROD("item/blaze_rod"),
        SLIME_BALL("item/slime_ball"), BONE("item/bone"), LEATHER("item/leather"),
        BRICK("item/brick"), HONEY_BOTTLE("item/honey_bottle"), SNOWBALL("item/snowball"),
        CRAFTING_TABLE("block/crafting_table_front"), BARREL("block/barrel_side"),
        FURNACE("block/furnace_front"), ANVIL("block/anvil"),
        LANTERN("item/lantern"), ENCHANTING_TABLE("block/enchanting_table_top");
        final String sprite;
        Icon(String sprite) { this.sprite = sprite; }
    }
    record ActionButton(TextComponent label, Action action, int width, Icon icon, String lore, UUID owner, String ownerName, Sprite customSprite) {
        ActionButton(TextComponent label, Action action, int width, Icon icon, String lore, UUID owner, String ownerName) { this(label, action, width, icon, lore, owner, ownerName, null); }
        ActionButton withSprite(Sprite sprite) { return new ActionButton(label, action, width, sprite == null ? icon : null, lore, owner, ownerName, sprite); }
        ActionButton(TextComponent label, Action action, int width, Icon icon, String lore) { this(label, action, width, icon, lore, null, null); }
        ActionButton(TextComponent label, Action action, int width, Icon icon) { this(label, action, width, icon, "Select this destination to travel to it."); }
    }
    record Sprite(Key atlas, Key texture) {
        static Sprite parse(String atlas, String texture) {
            if (atlas.length() > 256 || texture.length() > 256 || atlas.isBlank() || texture.isBlank())
                throw new IllegalArgumentException("Enter an atlas and sprite ID (up to 256 characters each).");
            try { return new Sprite(Key.key(atlas), Key.key(texture)); }
            catch (net.kyori.adventure.key.InvalidKeyException exception) {
                throw new IllegalArgumentException("Use valid namespace:path IDs.", exception);
            }
        }
        Component component() { return Component.object(ObjectContents.sprite(atlas, texture)); }
        String stored() { return "sprite|" + atlas.asString() + "|" + texture.asString(); }
    }
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
    static Key spriteAtlas() {
        // Item textures moved out of the blocks atlas in 1.21.11.
        String version = org.bukkit.Bukkit.getServer() == null ? "1.21.10" : org.bukkit.Bukkit.getMinecraftVersion();
        return spriteAtlas(version);
    }
    static Key spriteAtlas(String version) {
        String[] parts = version.split("\\.");
        boolean separate = Integer.parseInt(parts[0]) > 1 || parts.length > 2 && Integer.parseInt(parts[1]) == 21 && Integer.parseInt(parts[2]) >= 11
                || Integer.parseInt(parts[1]) > 21;
        return Key.key(separate ? "minecraft:items" : "minecraft:blocks");
    }
    static Component buttonLabel(ActionButton value) {
        Component text = value.label().paperText() == null ? Component.text(value.label().toPlainText()) : value.label().paperText();
        if (value.label().xpLevels() != null && value.label().xpLevels() > 0) {
            text = text.append(Component.object(ObjectContents.sprite(spriteAtlas(), Key.key("minecraft:item/experience_bottle"))))
                    .append(Component.text(" " + value.label().xpLevels()));
        }
        if (value.owner() != null) {
            text = text.append(Component.text(" · ")).append(Component.object(ObjectContents.playerHead(value.owner()))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Owner: " + value.ownerName()))));
        }
        if (value.label().afterOwner() != null) {
            var tail = value.label().afterOwner();
            text = text.append(tail.paperText() == null ? Component.text(tail.toPlainText()) : tail.paperText());
        }
        if (value.icon() == null && value.customSprite() == null) return prependLabelIcon(value, text);
        Component icon = value.customSprite() != null ? value.customSprite().component()
                : Component.object(ObjectContents.sprite(value.icon().sprite.startsWith("block/") ? Key.key("minecraft:blocks") : spriteAtlas(), Key.key("minecraft:" + value.icon().sprite)));
        if (value.label().toPlainText().isEmpty() && value.label().paperText() == null) return icon;
        return prependLabelIcon(value, icon.append(Component.text(" ")).append(text));
    }
    private static Component prependLabelIcon(ActionButton value, Component text) {
        return value.label().beforeIcon() == null ? text
                : value.label().beforeIcon().append(Component.text(" ")).append(text);
    }
    static io.papermc.paper.registry.data.dialog.ActionButton button(ActionButton value) {
        var action = value.action().dynamic() ? DialogAction.commandTemplate(value.action().command()) : DialogAction.staticAction(ClickEvent.runCommand(value.action().command()));
        return io.papermc.paper.registry.data.dialog.ActionButton.create(buttonLabel(value), Component.text(value.lore()), value.width(), action);
    }
}
