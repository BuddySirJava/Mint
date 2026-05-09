package ir.buddy.mint.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.logging.Level;


public final class MintMetricsBootstrap {

    private static final String METRICS_CLASS = "ir.buddy.mint.lib.bstats.bukkit.Metrics";

    private MintMetricsBootstrap() {
    }

    


    public static boolean tryEnable(JavaPlugin plugin, int serviceId) {
        ClassLoader pluginCl = plugin.getClass().getClassLoader();
        try {
            Class<?> metricsClass = Class.forName(METRICS_CLASS, true, pluginCl);
            Constructor<?> ctor;
            try {
                
                ctor = metricsClass.getConstructor(org.bukkit.plugin.Plugin.class, int.class);
            } catch (NoSuchMethodException e) {
                
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
