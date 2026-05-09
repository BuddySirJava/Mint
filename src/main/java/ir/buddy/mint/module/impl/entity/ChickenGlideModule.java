package ir.buddy.mint.module.impl.entity;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import ir.buddy.mint.util.ScheduledTaskHandle;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChickenGlideModule implements Module, Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, ScheduledTaskHandle> activeTasks = new HashMap<>();

    public ChickenGlideModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Chicken Glide";
    }

    @Override
    public String getConfigPath() {
        return "modules.chicken-glide";
    }

    @Override
    public String getDescription() {
        return "Carry chickens and glide with slow falling.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {

        for (Map.Entry<UUID, ScheduledTaskHandle> entry : activeTasks.entrySet()) {
            entry.getValue().cancel();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                removeChickenPassenger(player);
                player.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
        activeTasks.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player))
            return;
        if (!player.isSneaking())
            return;

        if (!player.getInventory().getItemInMainHand().getType().isAir())
            return;

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Chicken chicken))
            return;


        if (isCarryingChicken(player))
            return;

        event.setCancelled(true);


        player.addPassenger(chicken);

        UUID pid = player.getUniqueId();
        ScheduledTaskHandle handle = FoliaScheduler.runEntityAtFixedRate(
                plugin,
                player,
                0L,
                20L,
                () -> {
                    if (!player.isOnline() || !isCarryingChicken(player)) {
                        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                        ScheduledTaskHandle h = activeTasks.remove(pid);
                        if (h != null) {
                            h.cancel();
                        }
                        return;
                    }

                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOW_FALLING, 40, 0, false, false, true));
                },
                () -> activeTasks.remove(pid)
        );

        activeTasks.put(pid, handle);
    }


    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player))
            return;
        if (!event.isSneaking())
            return;
        if (!activeTasks.containsKey(player.getUniqueId()))
            return;

        removeChickenPassenger(player);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);

        ScheduledTaskHandle task = activeTasks.remove(player.getUniqueId());
        if (task != null)
            task.cancel();
    }

    private boolean isCarryingChicken(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Chicken)
                return true;
        }
        return false;
    }

    private void removeChickenPassenger(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Chicken) {
                player.removePassenger(passenger);
                return;
            }
        }
    }
}
