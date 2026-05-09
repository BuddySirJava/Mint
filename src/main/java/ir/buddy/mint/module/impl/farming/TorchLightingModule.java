package ir.buddy.mint.module.impl.farming;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.module.support.ModuleEventGuards;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TorchLightingModule implements Module, Listener {

    private final JavaPlugin plugin;

    public TorchLightingModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Torch Lighting";
    }

    @Override
    public String getConfigPath() {
        return "modules.torch-lighting";
    }

    @Override
    public String getDescription() {
        return "Sneak right-click with a torch to ignite lightable blocks.";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!ModuleEventGuards.isRightClickBlock(event.getAction())) {
            return;
        }
        if (event.getHand() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        if (!ModuleEventGuards.isEnabledForPlayerAndCanBuild(plugin, this, player, clickedBlock.getLocation())) {
            return;
        }

        ItemStack usedItem = ModuleEventGuards.heldItem(player, event.getHand());
        if (usedItem.getType() != Material.TORCH) {
            return;
        }

        if (isBlacklisted(clickedBlock.getType())) {
            return;
        }

        BlockData blockData = clickedBlock.getBlockData();
        if (!(blockData instanceof Lightable lightable)) {
            return;
        }
        if (lightable.isLit()) {
            return;
        }

        if (clickedBlock.getState() instanceof Furnace furnace) {
            
            short burnTicks = (short) Math.max(1, plugin.getConfig().getInt(getConfigPath() + ".furnace-burn-ticks", 200));
            furnace.setBurnTime(burnTicks);
            furnace.update(true, true);
        } else {
            lightable.setLit(true);
            clickedBlock.setBlockData(lightable, true);
        }
        clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 0.9f, 1.0f);
        if (player.getGameMode() != GameMode.CREATIVE) {
            usedItem.setAmount(usedItem.getAmount() - 1);
        }
        event.setCancelled(true);
    }

    private boolean isBlacklisted(Material material) {
        FileConfiguration config = plugin.getConfig();
        List<String> list = config.getStringList(getConfigPath() + ".blacklist");
        if (list.isEmpty()) {
            return false;
        }

        Set<String> normalized = new HashSet<>();
        for (String entry : list) {
            if (entry != null) {
                normalized.add(entry.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalized.contains(material.name());
    }
}
