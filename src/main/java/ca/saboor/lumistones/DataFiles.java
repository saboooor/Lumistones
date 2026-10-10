package ca.saboor.lumistones;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.file.*;
import java.io.IOException;

final class DataFiles {
    private DataFiles() {}
    static YamlConfiguration read(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) try { yaml.load(file); }
        catch (Exception e) { throw new IllegalStateException("Cannot read " + file + "; refusing to overwrite data", e); }
        return yaml;
    }
    static void write(JavaPlugin plugin, Path path, YamlConfiguration yaml) {
        try {
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, yaml.saveToString());
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save " + path, e); }
    }
}
