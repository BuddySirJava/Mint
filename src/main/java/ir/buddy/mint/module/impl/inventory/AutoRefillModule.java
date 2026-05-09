package ir.buddy.mint.module.impl.inventory;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoRefillModule implements Module, Listener {

    private final JavaPlugin plugin;

    public AutoRefillModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Auto Refill";
    }

    @Override
    public String getConfigPath() {
        return "modules.auto-refill";
    }

    @Override
    public String getDescription() {
        return "Automatically refills depleted block stacks on your hotbar from your inventory.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;

        ItemStack itemInHand = event.getItemInHand();


        if (itemInHand.getAmount() == 1) {
            EquipmentSlot hand = event.getHand();

            ItemStack template = itemInHand.clone();


            FoliaScheduler.runEntityLater(plugin, player, 1L, () -> handleRefill(player, hand, template));
        }
    }

    private void handleRefill(Player player, EquipmentSlot hand, ItemStack template) {
        if (!player.isOnline()) return;

        PlayerInventory inventory = player.getInventory();

        int targetSlot = (hand == EquipmentSlot.HAND) ? inventory.getHeldItemSlot() : 40;

        ItemStack currentItem = inventory.getItem(targetSlot);


        if (currentItem != null && !currentItem.getType().isAir() && currentItem.getAmount() > 0) {
            return;
        }

        int replacementSlot = findMatchingItemSlot(inventory, template);
        if (replacementSlot == -1) return;


        ItemStack replacementItem = inventory.getItem(replacementSlot);
        inventory.setItem(targetSlot, replacementItem.clone());
        inventory.setItem(replacementSlot, null);


        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 2.0f);


        player.updateInventory();
    }

    private int findMatchingItemSlot(PlayerInventory inventory, ItemStack template) {


        for (int i = 9; i <= 35; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.isSimilar(template)) {
                return i;
            }
        }


        for (int i = 0; i <= 8; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.isSimilar(template)) {
                return i;
            }
        }

        return -1;
    }
}
