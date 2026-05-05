package ir.buddy.mint.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads optional runtime jars (JDBC, Mongo sync driver, etc.) from {@code plugins/&lt;PluginName&gt;/lib/*.jar}.
 */
public final class MintJdbcLibraryLoader {

    private MintJdbcLibraryLoader() {
    }

    /**
     * @return a classloader over all {@code .jar} files in the plugin data {@code lib} folder,
     *         or {@code null} if the folder is missing or contains no jars
     */
    public static URLClassLoader tryCreate(JavaPlugin plugin) {
        File dir = new File(plugin.getDataFolder(), "lib");
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        List<URL> urls = new ArrayList<>();
        for (File f : files) {
            if (!f.isFile() || !f.getName().endsWith(".jar")) {
                continue;
            }
            try {
                urls.add(f.toURI().toURL());
            } catch (MalformedURLException ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid JDBC library path: " + f, ex);
            }
        }
        if (urls.isEmpty()) {
            return null;
        }
        return new URLClassLoader(urls.toArray(URL[]::new), plugin.getClass().getClassLoader());
    }
}
