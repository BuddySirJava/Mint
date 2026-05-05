package ir.buddy.mint.module.impl;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class InvisibleFrameModule implements Module, Listener {

    private final JavaPlugin plugin;

    public InvisibleFrameModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Invisible Frame";
    }

    @Override
    public String getConfigPath() {
        return "modules.invisible-frame";
    }

    @Override
    public String getDescription() {
        return "Toggle item frame visibility with shears.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) return;
        if (!event.getPlayer().isSneaking()) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame itemFrame)) return;
        if (!ModuleAccess.canBuild(plugin, event.getPlayer(), itemFrame.getLocation())) return;

        if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.SHEARS) return;

        boolean nowVisible = !itemFrame.isVisible();
        itemFrame.setVisible(nowVisible);

        itemFrame.getWorld().playSound(
            itemFrame.getLocation(),
            Sound.ENTITY_SHEEP_SHEAR
            ,
            1.0f,
            1.0f
        );


        event.setCancelled(true);
    }
}
