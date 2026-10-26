package ca.saboor.lumistones;

import ca.saboor.lumistones.DialogView.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Optional Floodgate bridge. No Floodgate classes are required to load the Paper plugin. */
final class BedrockForms {
    private final Lumistones plugin;
    private ClassLoader loader;
    private Class<?> apiClass;
    private Object api;
    BedrockForms(Lumistones plugin) { this.plugin = plugin; }
    void refresh() {
        api = null;
        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) return;
        try {
            loader = floodgate.getClass().getClassLoader();
            apiClass = type("org.geysermc.floodgate.api.FloodgateApi");
            api = apiClass.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) { plugin.getLogger().warning("Floodgate forms unavailable: " + e.getMessage()); }
    }
    boolean isBedrock(Player player) {
        if (api == null) return false;
        try { return (boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId()); }
        catch (ReflectiveOperationException e) { return false; }
    }
    void show(Player player, MultiActionDialog dialog) {
        try {
            List<Input> inputs = dialog.getBase().inputs() == null ? List.of() : dialog.getBase().inputs();
            boolean custom = !inputs.isEmpty();
            Class<?> formClass = type("org.geysermc.cumulus.form." + (custom ? "CustomForm" : "SimpleForm"));
            Class<?> builderClass = type(formClass.getName() + "$Builder");
            Class<?> genericBuilder = type("org.geysermc.cumulus.form.util.FormBuilder");
            Object builder = formClass.getMethod("builder").invoke(null);
            genericBuilder.getMethod("title", String.class).invoke(builder, dialog.getBase().title().toPlainText());
            List<ActionButton> buttons = new ArrayList<>(dialog.actions());
            buttons.add(dialog.exit());
            if (!custom) {
                StringBuilder content = new StringBuilder();
                if (dialog.getBase().body() != null) for (var body : dialog.getBase().body()) if (body instanceof PlainMessageBody message) content.append(message.contents().toPlainText()).append('\n');
                for (ActionButton button : buttons) content.append(buttonLabel(button)).append(": ").append(button.lore()).append('\n');
                builderClass.getMethod("content", String.class).invoke(builder, content.toString());
                for (ActionButton button : buttons) builderClass.getMethod("button", String.class).invoke(builder, buttonLabel(button));
            } else {
                for (Input input : inputs) {
                    if (input instanceof TextInput text) builderClass.getMethod("input", String.class, String.class, String.class).invoke(builder, text.label().toPlainText() + "\n" + buttons.stream().map(button -> buttonLabel(button) + ": " + button.lore()).collect(java.util.stream.Collectors.joining("\n")), "", text.initial() == null ? "" : text.initial());
                    else if (input instanceof BooleanInput bool) builderClass.getMethod("toggle", String.class, boolean.class).invoke(builder, bool.label().toPlainText(), Boolean.TRUE.equals(bool.initial()));
                    else if (input instanceof NumberRangeInput number) builderClass.getMethod("slider", String.class, float.class, float.class, float.class, float.class).invoke(builder, number.label().toPlainText(), number.start(), number.end(), number.step() == null ? 1f : number.step(), number.initial() == null ? number.start() : number.initial());
                    else throw new IllegalArgumentException("Unsupported form input " + input.getClass().getSimpleName());
                }
                builderClass.getMethod("dropdown", String.class, List.class, int.class).invoke(builder, "Action", buttons.stream().map(button -> buttonLabel(button)).toList(), 0);
            }
            Consumer<Object> callback = response -> {
                try {
                    Class<?> responseClass = type("org.geysermc.cumulus.response." + (custom ? "CustomFormResponse" : "SimpleFormResponse"));
                    int selected = ((Number) (custom ? responseClass.getMethod("asDropdown", int.class).invoke(response, inputs.size()) : responseClass.getMethod("clickedButtonId").invoke(response))).intValue();
                    if (selected < 0 || selected >= buttons.size()) return;
                    Map<String, String> values = new HashMap<>();
                    for (int i = 0; i < inputs.size(); i++) {
                        Input input = inputs.get(i);
                        String method = input instanceof TextInput ? "asInput" : input instanceof BooleanInput ? "asToggle" : "asSlider";
                        Object value = responseClass.getMethod(method, int.class).invoke(response, i);
                        values.put(input.key(), String.valueOf(value));
                    }
                    String command = command(buttons.get(selected).action(), values);
                    if (command == null || !plugin.isEnabled()) return;
                    // Floodgate callbacks may run on a network thread; never touch Bukkit state there.
                    Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) plugin.executeUiCommand(player, command); });
                } catch (ReflectiveOperationException | RuntimeException e) { plugin.getLogger().warning("Invalid Bedrock form response: " + e.getMessage()); }
            };
            genericBuilder.getMethod("validResultHandler", Consumer.class).invoke(builder, callback);
            boolean sent = (boolean) apiClass.getMethod("sendForm", UUID.class, genericBuilder).invoke(api, player.getUniqueId(), builder);
            if (!sent) fallback(player);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Could not send Bedrock form: " + e.getMessage()); fallback(player);
        }
    }
    static String buttonLabel(ActionButton button) {
        String label = button.label().toPlainText();
        if (label.isEmpty() && button.icon() != null) label = button.icon().name().replace('_', ' ');
        return label + (button.label().xpLevels() == null || button.label().xpLevels() <= 0 ? "" : button.label().xpLevels() + " XP") + (button.ownerName() == null ? "" : " · Owner: " + button.ownerName()) + (button.label().afterOwner() == null ? "" : button.label().afterOwner().toPlainText());
    }
    static String command(Action action, Map<String, String> values) {
        if (action == null) return null;
        String command = action.command();
        if (action.dynamic()) for (var entry : values.entrySet())
            command = command.replace("$(" + entry.getKey() + ")", Set.of("atlas", "sprite").contains(entry.getKey()) ? entry.getValue().trim() : TravelRules.cleanName(entry.getValue()));
        return command;
    }

    private void fallback(Player player) { player.sendMessage(plugin.error("Forms are unavailable. Use /lumistones list, tp <id>, rename <name>, private <id> <true|false>, or remove <id>.")); }
    private Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, true, loader); }
}
