package ca.saboor.lumistones;

import org.bukkit.configuration.Configuration;

record Cost(int levels, double money) {
    static final Cost FREE = new Cost(0, 0);
    Cost {
        levels = Math.max(0, levels);
        money = Double.isFinite(money) ? Math.max(0, money) : 0;
    }
    static Cost read(Configuration config, String path, String legacy, int fallback) {
        // Explicit old values take precedence over bundled defaults for upgraded servers.
        if (!config.contains(path, true) && legacy != null && config.contains(legacy, true))
            return new Cost(config.getInt(legacy), 0);
        if (config.contains(path, true))
            return new Cost(config.contains(path + ".levels", true) ? config.getInt(path + ".levels") : 0,
                    config.contains(path + ".money", true) ? config.getDouble(path + ".money") : 0);
        return new Cost(config.getInt(path + ".levels", fallback), config.getDouble(path + ".money", 0));
    }
    String label() {
        return money > 0 ? (levels > 0 ? levels + " XP + " : "") + money + " money" : levels + " XP";
    }
}
