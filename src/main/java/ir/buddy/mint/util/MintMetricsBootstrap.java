package ir.buddy.mint.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.logging.Level;

/** bStats is shaded into Mint under {@code ir.buddy.mint.lib.bstats}. */
public final class MintMetricsBootstrap {

    private static final String METRICS_CLASS = "ir.buddy.mint.lib.bstats.bukkit.Metrics";

    private MintMetricsBootstrap() {
    }

    /**
     * @return {@code true} if a {@code Metrics} instance was constructed
     */
    public static boolean tryEnable(JavaPlugin plugin, int serviceId) {
        ClassLoader pluginCl = plugin.getClass().getClassLoader();
        try {
            Class<?> metricsClass = Class.forName(METRICS_CLASS, true, pluginCl);
            Constructor<?> ctor;
            try {
                // bStats 3.0.2+ uses (Plugin, int) constructor
                ctor = metricsClass.getConstructor(org.bukkit.plugin.Plugin.class, int.class);
            } catch (NoSuchMethodException e) {
                // bStats 3.0.0 uses (JavaPlugin, int) constructor
                ctor = metricsClass.getConstructor(JavaPlugin.class, int.class);
            }
            ctor.newInstance(plugin, serviceId);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "bStats metrics unavailable.", ex);
            return false;
        }
    }
}
