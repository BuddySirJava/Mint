package ir.buddy.mint.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ProtectionSupport {

    private static final String BYPASS_PERMISSION = "mint.bypass.protection";

    public boolean canBuild(Player player, Location location) {
        if (player == null || location == null) {
            return true;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }

        return canBuildWithWorldGuard(player, location) && canBuildWithGriefPrevention(player, location);
    }

    private boolean canBuildWithWorldGuard(Player player, Location location) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (!(plugin instanceof WorldGuardPlugin wgPlugin)) {
            return true;
        }

        try {
            LocalPlayer localPlayer = wgPlugin.wrapPlayer(player);
            return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                    .testBuild(BukkitAdapter.adapt(location), localPlayer, Flags.BUILD);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean canBuildWithGriefPrevention(Player player, Location location) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (plugin == null || !plugin.isEnabled() || GriefPrevention.instance == null) {
            return true;
        }

        try {
            return GriefPrevention.instance.allowBuild(player, location) == null;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
