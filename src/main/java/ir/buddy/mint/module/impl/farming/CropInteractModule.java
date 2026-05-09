package ir.buddy.mint.module.impl.farming;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.module.support.ModuleEventGuards;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class CropInteractModule implements Module, Listener {

    private final JavaPlugin plugin;

    public CropInteractModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Crop Interact";
    }

    @Override
    public String getConfigPath() {
        return "modules.crop-interact";
    }

    @Override
    public String getDescription() {
        return "Right-click mature crops to harvest and automatically replant.";
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
    public void onCropInteract(PlayerInteractEvent event) {
        if (!ModuleEventGuards.isRightClickBlock(event.getAction())) {
            return;
        }
        if (!ModuleEventGuards.isMainHand(event.getHand())) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!ModuleEventGuards.isEnabledForPlayerAndCanBuild(plugin, this, player, block.getLocation())) {
            return;
        }

        Material replantDropType = getReplantDropType(block.getType());
        if (replantDropType == null) {
            return;
        }

        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) {
            return;
        }
        if (ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        Collection<ItemStack> vanillaDrops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        Map<Material, Integer> mergedDrops = mergeDrops(vanillaDrops);
        if (!consumeReplantItem(mergedDrops, replantDropType)) {
            return;
        }

        ageable.setAge(0);
        block.setBlockData(ageable, false);
        dropMerged(block, mergedDrops);
        event.setCancelled(true);
    }

    private Material getReplantDropType(Material cropType) {
        return switch (cropType) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    private Map<Material, Integer> mergeDrops(Collection<ItemStack> drops) {
        Map<Material, Integer> merged = new HashMap<>();
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            merged.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        return merged;
    }

    private boolean consumeReplantItem(Map<Material, Integer> mergedDrops, Material replantType) {
        int amount = mergedDrops.getOrDefault(replantType, 0);
        if (amount <= 0) {
            return false;
        }
        if (amount == 1) {
            mergedDrops.remove(replantType);
            return true;
        }
        mergedDrops.put(replantType, amount - 1);
        return true;
    }

    private void dropMerged(Block block, Map<Material, Integer> mergedDrops) {
        for (Map.Entry<Material, Integer> entry : mergedDrops.entrySet()) {
            int left = entry.getValue();
            while (left > 0) {
                int chunk = Math.min(left, entry.getKey().getMaxStackSize());
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(entry.getKey(), chunk));
                left -= chunk;
            }
        }
    }
}
