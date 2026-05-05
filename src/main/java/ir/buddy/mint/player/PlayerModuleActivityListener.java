package ir.buddy.mint.player;

import ir.buddy.mint.MintPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerModuleActivityListener implements Listener {

    private final MintPlugin plugin;

    public PlayerModuleActivityListener(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlayerModulePreferences().preloadToggles(
                java.util.List.of(event.getPlayer()),
                plugin.getModuleManager().getModules()
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerModulePreferences().clearCachedToggles(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        plugin.getPlayerModulePreferences().clearCachedToggles(event.getPlayer().getUniqueId());
    }
}
