package ir.buddy.mint.module.impl.inventory;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ShiftRightClickSortModule implements Module, Listener {

    private final JavaPlugin plugin;

    public ShiftRightClickSortModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Shift Right Click Sort";
    }

    @Override
    public String getConfigPath() {
        return "modules.shift-right-click-sort";
    }

    @Override
    public String getDescription() {
        return "Shift-right-click inventories to sort instantly.";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
    }

    @EventHandler(ignoreCancelled = true)
    public void onShiftRightClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player))
            return;

        boolean shift = event.isShiftClick();
        boolean right = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;

        if (!shift || !right)
            return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null)
            return;

        if (isBlockedInventory(clickedInv))
            return;

        event.setCancelled(true);

        FoliaScheduler.runEntity(plugin, player, () -> sortInventory(clickedInv, player));
    }

    private boolean isBlockedInventory(Inventory inv) {

        InventoryHolder holder = inv.getHolder();


        if (holder == null)
            return true;


        String name = holder.getClass().getName().toLowerCase();
        if (name.contains("slimefun") ||
                name.contains("blockmenu") ||
                name.contains("slimefuninventory") ||
                name.contains("chestmenu") ||
                name.contains("menu")) {
            return true;
        }


        if (holder instanceof DoubleChest) {
            return false;
        }


        if (holder instanceof org.bukkit.block.Container) {
            return false;
        }


        return true;
    }

    private void sortInventory(Inventory inv, Player player) {

        ItemStack[] raw = inv.getContents();
        List<ItemStack> items = new ArrayList<>();


        for (ItemStack item : raw) {
            if (item != null && item.getType().isItem()) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty())
            return;


        inv.clear();


        items.sort(
                Comparator.comparing((ItemStack i) -> i.getType().getKey().toString())
                        .thenComparing(i -> i.hasItemMeta() ? i.getItemMeta().toString() : ""));


        for (ItemStack stack : items) {
            inv.addItem(stack);
        }


        player.updateInventory();
    }
}
