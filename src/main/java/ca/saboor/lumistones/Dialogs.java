package ca.saboor.lumistones;

import ca.saboor.lumistones.DialogView.*;

import org.bukkit.entity.Player;
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
        return new ActionButton(new TextComponent(label), action, 300, icon);
    }
    static MultiActionDialog menu(DialogBase base, List<ActionButton> actions) {
        return new MultiActionDialog(base, actions, 1, button("Close", "/lumistones close", false, Icon.CLOSE));
    }
    static MultiActionDialog editingMenu(DialogBase base, List<ActionButton> actions, String prefix) {
        return new MultiActionDialog(base.canCloseWithEscape(true), actions.stream().map(action -> new ActionButton(action.label(), action.action(), 150, action.icon())).toList(), 2,
                button("Close", prefix + "rename $(name)", true, Icon.CLOSE));
    }
    void destinations(Player player, Stone source, boolean scroll) {
        Lumistones.Session session = plugin.session(player, source, scroll);
        DialogBase base = base(source == null ? "Warp Scroll" : source.name(), "Choose a waystone you have visited and activated. Stay still during the teleport warmup.");
        List<ActionButton> actions = new ArrayList<>();
        for (Stone target : plugin.stones.values().stream().sorted(Comparator.comparing(Stone::name)).toList()) {
            if (!plugin.known(player, target) || target.location().getWorld() == null || source != null && source.id().equals(target.id())) continue;
            if (!plugin.getConfig().getBoolean("teleport.cross-world") && !target.world().equals(player.getWorld().getUID())) continue;
            actions.add(button(target.name() + " · " + target.location().getWorld().getName() + " · " + plugin.cost(player, target, scroll) + " levels", "/lumistones choose " + session.token() + " " + target.id(), false, Icon.TRAVEL));
        }
        if (actions.isEmpty()) actions.add(button("No other activated waystones", "/lumistones close", false));
        plugin.showDialog(player, menu(base, actions));
    }
    void manage(Player player, Stone stone) {
        var session = plugin.session(player, stone, false);
        DialogBase base = base("Manage " + stone.name(), "Rename your waystone. Break either wall block to retrieve it. Close saves the name. Private stones can only be activated and used by their owner. Other stones require each player to visit and activate them.");
        base.inputs(List.of(new TextInput("name", new TextComponent("Name")).initial(stone.name()).maxLength(32)));
        List<ActionButton> actions = new ArrayList<>(); String prefix = "/lumistones edit " + session.token() + " ";
        actions.add(button("Travel", prefix + "travel", false, Icon.TRAVEL));
        actions.add(button(stone.privateAccess() ? "Allow other players" : "Make private", prefix + "private", false, stone.privateAccess() ? Icon.SHARED : Icon.PRIVATE));
        plugin.showDialog(player, editingMenu(base, actions, prefix));
    }
}
