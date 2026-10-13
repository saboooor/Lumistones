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
        return new MultiActionDialog(base.canCloseWithEscape(true), actions.stream().map(action -> new ActionButton(action.label(), new Action(action.action().command() + " $(name)", true), 150, action.icon(), action.lore())).toList(), 2,
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
                    new Action("/lumistones choose " + session.token() + " " + target.id(), false), 300, target.icon(), "Owner: " + ownerName + ". Select this destination to teleport there." + (cost.levels() > 0 || cost.money() > 0 ? " Travel costs are charged after a successful teleport." : ""), owner, ownerName));
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
        DialogBase base = base("Manage " + stone.name(), "Rename your waystone. Shift-right-click with a block to change its body material. Break either wall block to retrieve it. Every button saves the name. Escape dismisses without saving. Private stones can only be activated and used by their owner. Global stones are available to everyone without activation. Local stones require each player to visit and activate them.");
        base.inputs(List.of(new TextInput("name", new TextComponent("Name")).initial(stone.name()).maxLength(32)));
        List<ActionButton> actions = new ArrayList<>(); String prefix = "/lumistones edit " + session.token() + " ";
        actions.add(button("Change icon", prefix + "icon", false, stone.icon()));
        actions.add(button("Travel", prefix + "travel", false, Icon.TRAVEL));
        actions.add(button(stone.privateAccess() ? "Allow other players" : "Make private", prefix + "private", false, stone.privateAccess() ? Icon.SHARED : Icon.PRIVATE));
        if (plugin.getConfig().getBoolean("waystones.public.enabled", false) || stone.publicAccess())
            actions.add(button(stone.publicAccess() ? "Make local" : "Make global · " + plugin.publicCost().label(), prefix + "public", false, Icon.SHARED));
        if (!stone.owner().equals(new UUID(0, 0)))
            actions.add(button(stone.ownerHead() ? "Hide owner head" : "Show owner head", prefix + "owner-head", false, Icon.OWNER));
        plugin.showDialog(player, editingMenu(base, actions, prefix));
    }
    void confirmLocal(Player player, Stone stone, String token) {
        plugin.showDialog(player, localConfirmation(stone, token));
    }
    static MultiActionDialog localConfirmation(Stone stone, String token) {
        String prefix = "/lumistones edit " + token + " ";
        return new MultiActionDialog(base("Make local?", "Make " + stone.name() + " local? Players will need to discover it to use it. The currency and XP paid to make it global will not be refunded."),
                List.of(button("Confirm · Make local", prefix + "confirm-local", false, Icon.SHARED)), 1,
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
        List<ActionButton> actions = Arrays.stream(Icon.values()).map(icon -> new ActionButton(
                new TextComponent(""), new Action("/lumistones edit " + token + " set-icon " + icon.name(), false), 20, icon,
                icon.name().replace('_', ' ') + (icon == stone.icon() ? " (selected). " : ". ")
                        + "Use this icon beside the waystone in the destination menu.")).toList();
        return new MultiActionDialog(base("Waystone icon", "Choose the icon for " + stone.name() + "."), actions, 14,
                button("Back", "/lumistones edit " + token + " back", false, Icon.CLOSE));
    }

}
