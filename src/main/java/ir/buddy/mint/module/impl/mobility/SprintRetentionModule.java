package ir.buddy.mint.module.impl.mobility;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SprintRetentionModule implements Module, Listener {

    private final MintPlugin plugin;
    private boolean registered = false;

    
    private static final int SPEED_DURATION_TICKS = 25;
    private static final int SPEED_AMPLIFIER = 0;

    public SprintRetentionModule(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Sprint Retention";
    }

    @Override
    public String getConfigPath() {
        return "modules.sprintretention";
    }

    @Override
    public String getDescription() {
        return "Brief Speed after releasing sprint so momentum lingers.";
    }

    @Override
    public void enable() {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        registered = false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        if (event.isSprinting())
            return;
        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player))
            return;

        player.addPotionEffect(new PotionEffect(
            PotionEffectType.SPEED,
            SPEED_DURATION_TICKS,
            SPEED_AMPLIFIER,
            false,
            false,
            false
        ));
    }
}
