package ir.buddy.mint.util;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModuleAccess {

    private ModuleAccess() {
    }

    public static boolean isEnabledForPlayer(JavaPlugin plugin, Module module, Player player) {
        if (plugin instanceof MintPlugin mintPlugin) {
            return mintPlugin.getPlayerModulePreferences().isEnabledFor(player, module);
        }
        return true;
    }

    public static boolean canBuild(JavaPlugin plugin, Player player, Location location) {
        if (plugin instanceof MintPlugin mintPlugin) {
            return mintPlugin.getProtectionSupport().canBuild(player, location);
        }
        return true;
    }
}
