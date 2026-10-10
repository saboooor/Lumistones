package ca.saboor.lumistones;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class TextStyles {
    private TextStyles() {}
    static Component component(String text) { return LegacyComponentSerializer.legacySection().deserialize(text); }
    static String plain(String text) { return PlainTextComponentSerializer.plainText().serialize(component(text)); }
    static String legacy(String text) {
        return java.util.regex.Pattern.compile("&([0-9a-fk-or])", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text)
                .replaceAll(match -> "§" + match.group(1).toLowerCase(java.util.Locale.ROOT));
    }
    static String prefix(String value) {
        String[] names = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "obfuscated", "bold", "strikethrough", "underline", "italic", "reset"};
        String codes = "0123456789abcdefklmnor";
        for (int i = 0; i < names.length; i++) value = value.replace("<" + names[i] + ">", "&" + codes.charAt(i)).replace("</" + names[i] + ">", "&r");
        return legacy(value);
    }
}
