package ir.buddy.mint.module.impl;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoToolModule implements Module, Listener {

    private final JavaPlugin plugin;

    public AutoToolModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Auto Tool";
    }

    @Override
    public String getConfigPath() {
        return "modules.auto-tool";
    }

    @Override
    public String getDescription() {
        return "Automatically switches to the proper tool when breaking blocks.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerHitBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player.getGameMode().equals(GameMode.CREATIVE)) return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() == Material.AIR) return;

        PlayerInventory inventory = player.getInventory();
        ItemStack currentItem = inventory.getItemInMainHand();


        if (isProperTool(currentItem, block)) {
            return;
        }


        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (isProperTool(item, block)) {
                inventory.setHeldItemSlot(i);
                return;
            }
        }


        for (int i = 9; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (isProperTool(item, block)) {
                int currentSlot = inventory.getHeldItemSlot();


                inventory.setItem(i, currentItem);
                inventory.setItem(currentSlot, item);
                return;
            }
        }
    }


    private boolean isProperTool(ItemStack item, Block block) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material blockType = block.getType();
        String itemName = item.getType().name();


        if (Tag.MINEABLE_PICKAXE.isTagged(blockType)) {
            return itemName.endsWith("_PICKAXE");
        }
        if (Tag.MINEABLE_AXE.isTagged(blockType)) {
            return itemName.endsWith("_AXE");
        }
        if (Tag.MINEABLE_SHOVEL.isTagged(blockType)) {
            return itemName.endsWith("_SHOVEL") || itemName.endsWith("_SPADE");
        }
        if (Tag.MINEABLE_HOE.isTagged(blockType)) {
            return itemName.endsWith("_HOE");
        }


        if (blockType == Material.COBWEB) {
            return itemName.endsWith("_SWORD") || item.getType() == Material.SHEARS;
        }
        if (Tag.LEAVES.isTagged(blockType)) {
            return item.getType() == Material.SHEARS || itemName.endsWith("_HOE");
        }


        return false;
    }
}
