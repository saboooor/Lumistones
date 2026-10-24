package ca.saboor.lumistones;

import ca.saboor.lumistones.DialogView.*;

import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.object.ObjectContents;
import java.util.*;

final class Dialogs {
    private final Lumistones plugin;
    Dialogs(Lumistones plugin) { this.plugin = plugin; }
    static DialogBase base(String title, String message) {
        return new DialogBase(new TextComponent(title))
            .body(List.of(new PlainMessageBody(new TextComponent(message), 350)));
    }
    static ActionButton button(String label, String command, boolean dynamic) {
        return button(label, command, dynamic, Icon.INFO);
    }
    static ActionButton button(String label, String command, boolean dynamic, Icon icon) {
        Action action = new Action(command, dynamic);
        return new ActionButton(new TextComponent(label), action, 300, icon, description(command));
    }
    static String description(String command) {
        if (command.contains(" discovered-by")) return "Save the name and see who has discovered this waystone.";
        if (command.contains(" back")) return "Return to waystone management with the selected icon saved.";
        if (command.contains("flightzone") && command.contains("save")) return "Save the plot name, width, and height. Players inside the plot can fly.";
        if (command.contains("remove")) return "Remove this flight plot and its flight permissions.";
        if (command.contains("rename")) return "Save the edited waystone name and close this menu. Escape dismisses without saving.";
        if (command.contains("owner-head")) return "Save the name and toggle the owner's head on the waystone model.";
        if (command.contains("private")) return "Save the name and toggle owner-only access. Shared discoverable stones require each player to visit and activate them.";
        if (command.contains("public")) return "Save the name and toggle global access. Global stones are available to everyone without activation; enabling this charges the configured cost.";
        if (command.contains("set-icon")) return "Use this icon beside the waystone in the destination menu.";
        if (command.contains("icon")) return "Save the name and choose the icon shown beside this waystone in the destination menu.";
        if (command.contains("travel")) return "Save the name and open the list of activated destinations. Select a destination to teleport there.";
        if (command.contains("close")) return "Close this menu without travelling.";
        return "Select this destination to teleport there. Travel costs are charged after a successful teleport.";
    }
    static MultiActionDialog menu(DialogBase base, List<ActionButton> actions) {
        return new MultiActionDialog(base, actions, 1, button("Close", "/lumistones close", false, Icon.CLOSE));
    }
    static MultiActionDialog editingMenu(DialogBase base, List<ActionButton> actions, String prefix) {
        return new MultiActionDialog(base.canCloseWithEscape(true), actions.stream().map(action -> new ActionButton(action.label(), new Action(action.action().command() + " $(name)", true), 150, action.icon(), action.lore()).withSprite(action.customSprite())).toList(), 2,
                button("Close", prefix + "rename $(name)", true, Icon.CLOSE));
    }
    void destinations(Player player, Stone source, boolean scroll) {
        if (!plugin.worldEnabled(player.getWorld())) { player.sendMessage(plugin.error("Waystones are disabled in this world.")); return; }
        Lumistones.Session session = plugin.session(player, source, scroll);
        DialogBase base = base(source == null ? "Warp Scroll" : source.name(), "Choose a waystone you have visited and activated." + (plugin.getConfig().getLong("teleport.warmup-seconds") > 0 ? " Stay still during the teleport warmup." : ""));
        List<ActionButton> actions = new ArrayList<>();
        for (Stone target : plugin.stones.values().stream().sorted(Comparator.comparing(Stone::name)).toList()) {
            if (!plugin.known(player, target) || !plugin.worldEnabled(target.location().getWorld()) || source != null && source.id().equals(target.id())) continue;
            if (!plugin.getConfig().getBoolean("teleport.cross-world") && !target.world().equals(player.getWorld().getUID())) continue;
            UUID owner = target.owner().equals(new UUID(0, 0)) ? null : target.owner();
            String ownerName = owner == null ? "Naturally generated" : org.bukkit.Bukkit.getOfflinePlayer(owner).getName();
            if (ownerName == null) ownerName = owner.toString();
            Cost cost = plugin.travelCost(player, target, scroll);
            actions.add(new ActionButton(destinationLabel(plugin.getConfig(), target.name(), target.location().getWorld().getName(), cost, target.world().equals(player.getWorld().getUID())),
                    new Action("/lumistones choose " + session.token() + " " + target.id(), false), 300, target.icon(), "Owner: " + ownerName + ". Select this destination to teleport there." + (cost.levels() > 0 || cost.money() > 0 ? " Travel costs are charged after a successful teleport." : ""), owner, ownerName).withSprite(target.customSprite()));
        }
        if (actions.isEmpty()) actions.add(button("No other activated waystones", "/lumistones close", false));
        plugin.showDialog(player, menu(base, actions));
    }
    static TextComponent destinationLabel(org.bukkit.configuration.Configuration config, String name, String world, Cost cost) {
        return destinationLabel(config, name, world, cost, false);
    }
    static TextComponent destinationLabel(org.bukkit.configuration.Configuration config, String name, String world, Cost cost, boolean sameWorld) {
        TextComponent levels = destinationLabel(config, name, world, cost.levels(), sameWorld);
        if (cost.money() == 0) return levels;
        String suffix = (cost.levels() > 0 ? "" : " · ") + new Cost(0, cost.money()).label() + (cost.levels() > 0 ? " + " : "");
        return new TextComponent(levels.toPlainText() + suffix, levels.xpLevels(), levels.paperText().append(Component.text(suffix)));
    }
    static TextComponent destinationLabel(org.bukkit.configuration.Configuration config, String name, String world, int cost) {
        return destinationLabel(config, name, world, cost, false);
    }
    static TextComponent destinationLabel(org.bukkit.configuration.Configuration config, String name, String world, int cost, boolean sameWorld) {
        String plain = name;
        Component text = Component.text(name);
        if (!sameWorld) {
            String alias = worldAlias(config, world);
            plain += " · " + alias;
            text = Component.text(name + " · ");
            String sprite = worldSprite(config, world);
            if (sprite != null) text = text.append(Component.object(ObjectContents.sprite(DialogView.spriteAtlas(), Key.key(sprite)))).append(Component.text(" "));
            text = text.append(Component.text(alias + (cost > 0 ? " · " : "")));
        } else if (cost > 0) text = text.append(Component.text(" · "));
        if (cost > 0) plain += " · ";
        return new TextComponent(plain, cost > 0 ? cost : null, text);
    }
    static String worldSprite(org.bukkit.configuration.Configuration config, String world) {
        var aliases = config.getConfigurationSection("enabled-worlds");
        Object configured = aliases == null ? null : aliases.getValues(true).get(world + ".sprite");
        String sprite = configured instanceof String value ? value : switch (world) {
            case "world" -> "minecraft:item/compass_16";
            case "world_nether" -> "minecraft:item/nether_star";
            case "world_the_end" -> "minecraft:item/ender_eye";
            default -> "";
        };
        if (sprite.isBlank()) return null;
        try { return Key.key(sprite).asString(); }
        catch (net.kyori.adventure.key.InvalidKeyException exception) { return null; }
    }
    static String worldAlias(org.bukkit.configuration.Configuration config, String world) {
        var aliases = config.getConfigurationSection("enabled-worlds");
        if (aliases != null) {
            var values = aliases.getValues(true);
            Object value = values.get(world + ".name");
            if (value == null) value = values.get(world); // Support the previous string-only aliases.
            if (value instanceof String alias && !alias.isBlank()) return alias;
        }
        return switch (world) {
            case "world" -> "Overworld";
            case "world_nether" -> "Nether";
            case "world_the_end" -> "The End";
            default -> world;
        };
    }
    void manage(Player player, Stone stone) {
        var session = plugin.session(player, stone, false);
        DialogBase base = base("Manage " + stone.name(), "Rename your waystone. Shift-right-click with a block to change its body material. Break either wall block to retrieve it. Every button saves the name. Escape dismisses without saving. Private stones can only be activated and used by their owner. Global stones are available to everyone without activation. Public stones require each player to visit and activate them.");
        base.inputs(List.of(new TextInput("name", new TextComponent("Name")).initial(stone.name()).maxLength(32)));
        List<ActionButton> actions = new ArrayList<>(); String prefix = "/lumistones edit " + session.token() + " ";
        actions.add(button("Change icon", prefix + "icon", false, stone.icon()).withSprite(stone.customSprite()));
        actions.add(button("Travel", prefix + "travel", false, Icon.TRAVEL));
        actions.add(button("Discovered by", prefix + "discovered-by", false, Icon.OWNER));
        actions.add(button("Visibility · " + (stone.privateAccess() ? "Private" : stone.publicAccess() ? "Global" : "Public"), prefix + "visibility", false, stone.privateAccess() ? Icon.PRIVATE : Icon.SHARED));
        if (!stone.owner().equals(new UUID(0, 0)))
            actions.add(button(stone.ownerHead() ? "Hide owner head" : "Show owner head", prefix + "owner-head", false, Icon.OWNER));
        plugin.showDialog(player, editingMenu(base, actions, prefix));
    }
    void visibility(Player player, Stone stone, String token) {
        plugin.showDialog(player, visibilityMenu(stone, token,
                plugin.getConfig().getBoolean("waystones.public.enabled", false), plugin.publicCost().label()));
    }
    static MultiActionDialog visibilityMenu(Stone stone, String token, boolean globalEnabled, String globalCost) {
        String prefix = "/lumistones edit " + token + " ";
        String selected = stone.privateAccess() ? "Private" : stone.publicAccess() ? "Global" : "Public";
        return new MultiActionDialog(base("Visibility · " + stone.name(),
                "Private: only the owner can activate and use it. Public: players must discover it before use. Global: everyone can use it without discovery. Current: " + selected + "."
                        + (globalEnabled ? "" : " Global waystones are disabled.")),
                List.of(button("Private" + (selected.equals("Private") ? " (selected)" : ""), prefix + "set-private", false, Icon.PRIVATE),
                        button("Public" + (selected.equals("Public") ? " (selected)" : ""), prefix + "set-public", false, Icon.SHARED),
                        button("Global" + (selected.equals("Global") ? " (selected)" : globalEnabled ? " · " + globalCost : " (disabled)"), prefix + "set-global", false, Icon.SHARED)),
                1, button("Back", prefix + "back", false, Icon.CLOSE));
    }
    void discoveredBy(Player player, Stone stone, String token) {
        List<String> names = new ArrayList<>();
        plugin.discoveries.forEach((id, stones) -> {
            if (stones.contains(stone.id())) {
                String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
                names.add(name == null || name.isBlank() ? id.toString() : name);
            }
        });
        plugin.showDialog(player, discoveryDetails(stone, token, names));
    }
    static MultiActionDialog discoveryDetails(Stone stone, String token, List<String> names) {
        String message = names.isEmpty() ? "No players have discovered this Lumistone yet."
                : "Discovered by (" + names.size() + "):\n" + String.join("\n", names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        return menu(base("Discovered by · " + stone.name(), message),
                List.of(button("Back", "/lumistones edit " + token + " back", false, Icon.CLOSE)));
    }
    void confirmLocal(Player player, Stone stone, String token) {
        plugin.showDialog(player, localConfirmation(stone, token));
    }
    static MultiActionDialog localConfirmation(Stone stone, String token) {
        String prefix = "/lumistones edit " + token + " ";
        return new MultiActionDialog(base("Make public?", "Make " + stone.name() + " public? Players will need to discover it to use it. The currency and XP paid to make it global will not be refunded."),
                List.of(button("Confirm · Make public", prefix + "confirm-local", false, Icon.SHARED)), 1,
                button("Cancel", prefix + "back", false, Icon.CLOSE));
    }
    void confirmPrivate(Player player, Stone stone, String token) {
        plugin.showDialog(player, privateConfirmation(stone, token));
    }
    static MultiActionDialog privateConfirmation(Stone stone, String token) {
        String prefix = "/lumistones edit " + token + " ";
        return new MultiActionDialog(base("Make private?", "Make " + stone.name() + " private? This removes its global status and only its owner will be able to use it. The currency and XP paid to make it global will not be refunded."),
                List.of(button("Confirm · Make private", prefix + "confirm-private", false, Icon.PRIVATE)), 1,
                button("Cancel", prefix + "back", false, Icon.CLOSE));
    }
    void icons(Player player, Stone stone) {
        var session = plugin.session(player, stone, false);
        plugin.showDialog(player, iconMenu(stone, session.token().toString()));
    }
    static MultiActionDialog iconMenu(Stone stone, String token) {
        List<ActionButton> actions = new ArrayList<>(Arrays.stream(Icon.values()).map(icon -> new ActionButton(
                new TextComponent(""), new Action("/lumistones edit " + token + " set-icon " + icon.name(), false), 20, icon,
                icon.name().replace('_', ' ') + (stone.customSprite() == null && icon == stone.icon() ? " (selected). " : ". ")
                        + "Use this icon beside the waystone in the destination menu.")).toList());
        actions.add(new ActionButton(new TextComponent("Custom"), new Action("/lumistones edit " + token + " custom-icon", false), 50, Icon.INFO, "Define an atlas sprite and preview it before applying."));
        return new MultiActionDialog(base("Waystone icon", "Choose the icon for " + stone.name() + "."), actions, 14,
                button("Back", "/lumistones edit " + token + " back", false, Icon.CLOSE));
    }

    static MultiActionDialog customIconMenu(Stone stone, String token, Sprite preview, String error) {
        Sprite current = preview != null ? preview : stone.customSprite();
        String prefix = "/lumistones edit " + token + " ";
        DialogBase base = base("Custom waystone icon", error != null ? error :
                "Enter atlas and sprite IDs. Preview before applying. Missing textures require a matching client resource pack. Java clients display the sprite; Bedrock forms show its ID.");
        base.inputs(List.of(new TextInput("atlas", new TextComponent("Atlas ID")).maxLength(256)
                        .initial(current == null ? DialogView.spriteAtlas().asString() : current.atlas().asString()),
                new TextInput("sprite", new TextComponent("Sprite ID")).maxLength(256)
                        .initial(current == null ? "minecraft:item/diamond" : current.texture().asString())));
        List<ActionButton> actions = new ArrayList<>();
        actions.add(button("Preview", prefix + "preview-icon $(atlas) $(sprite)", true, Icon.SPYGLASS));
        if (preview != null) actions.add(new ActionButton(new TextComponent("Apply · " + preview.texture().asString()),
                new Action(prefix + "apply-icon " + preview.atlas().asString() + " " + preview.texture().asString(), false),
                300, null, "Save this custom sprite as the waystone icon.").withSprite(preview));
        return new MultiActionDialog(base, actions, 1, button("Back", prefix + "icon", false, Icon.CLOSE));
    }
}
