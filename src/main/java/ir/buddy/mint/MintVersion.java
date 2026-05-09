package ir.buddy.mint;

import org.bukkit.plugin.java.JavaPlugin;




public final class MintVersion {

    private MintVersion() {
    }

    


    public static String plugin(JavaPlugin plugin) {
        try {
            return plugin.getPluginMeta().getVersion();
        } catch (NoSuchMethodError ex) {
            return plugin.getDescription().getVersion();
        }
    }
}
