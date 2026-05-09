package ir.buddy.mint.module.impl.inventory;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExpandedItemInteractionsModule implements Module, Listener {

    private final JavaPlugin plugin;

    public ExpandedItemInteractionsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Expanded Item Interactions";
    }

    @Override
    public String getConfigPath() {
        return "modules.expanded-item-interactions";
    }

    @Override
    public String getDescription() {
        return "Adds shulker insert, armor swap, and lava-item destroy interactions in inventories.";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryRightClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            return;
        }
        if (!isRightClick(event.getClick())) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (tryInsertIntoShulker(cursor, current, true, event)) {
            return;
        }
        if (tryInsertIntoShulker(current, cursor, false, event)) {
            return;
        }
        if (tryArmorSwap(player, cursor, current, event)) {
            return;
        }
        tryLavaDestroy(cursor, current, event);
    }

    private boolean tryInsertIntoShulker(ItemStack source, ItemStack shulkerCandidate, boolean sourceIsCursor, InventoryClickEvent event) {
        if (!isItem(source) || isShulkerBox(source)) {
            return false;
        }
        if (!isShulkerBox(shulkerCandidate)) {
            return false;
        }

        ItemStack shulkerClone = shulkerCandidate.clone();
        if (!insertSingleItemIntoShulker(shulkerClone, source)) {
            return false;
        }

        ItemStack updatedSource = decrementOne(source);
        event.setCancelled(true);
        if (sourceIsCursor) {
            event.setCursor(updatedSource);
            event.setCurrentItem(shulkerClone);
        } else {
            event.setCurrentItem(updatedSource);
            event.setCursor(shulkerClone);
        }
        return true;
    }

    private boolean insertSingleItemIntoShulker(ItemStack shulkerItem, ItemStack source) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return false;
        }
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return false;
        }

        Inventory inventory = shulkerBox.getInventory();
        ItemStack oneItem = source.clone();
        oneItem.setAmount(1);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (!isItem(existing)) {
                continue;
            }
            if (!existing.isSimilar(oneItem)) {
                continue;
            }
            if (existing.getAmount() >= existing.getMaxStackSize()) {
                continue;
            }

            existing.setAmount(existing.getAmount() + 1);
            inventory.setItem(slot, existing);
            blockStateMeta.setBlockState(shulkerBox);
            shulkerItem.setItemMeta(blockStateMeta);
            return true;
        }

        int emptySlot = inventory.firstEmpty();
        if (emptySlot == -1) {
            return false;
        }

        inventory.setItem(emptySlot, oneItem);
        blockStateMeta.setBlockState(shulkerBox);
        shulkerItem.setItemMeta(blockStateMeta);
        return true;
    }

    private boolean tryArmorSwap(Player player, ItemStack cursor, ItemStack current, InventoryClickEvent event) {
        if (isItem(cursor)) {
            return false;
        }
        if (!isArmor(current)) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        Material type = current.getType();
        ItemStack newArmorPiece = current.clone();
        ItemStack previousArmor;

        switch (type.getEquipmentSlot()) {
            case HEAD -> {
                previousArmor = inventory.getHelmet();
                inventory.setHelmet(newArmorPiece);
            }
            case CHEST -> {
                previousArmor = inventory.getChestplate();
                inventory.setChestplate(newArmorPiece);
            }
            case LEGS -> {
                previousArmor = inventory.getLeggings();
                inventory.setLeggings(newArmorPiece);
            }
            case FEET -> {
                previousArmor = inventory.getBoots();
                inventory.setBoots(newArmorPiece);
            }
            default -> {
                return false;
            }
        }

        event.setCancelled(true);
        event.setCurrentItem(isItem(previousArmor) ? previousArmor : null);
        return true;
    }

    private boolean tryLavaDestroy(ItemStack cursor, ItemStack current, InventoryClickEvent event) {
        if (isItem(cursor) && current != null && current.getType() == Material.LAVA_BUCKET && isDestroyable(cursor)) {
            event.setCancelled(true);
            event.setCursor(decrementOne(cursor));
            return true;
        }
        if (cursor != null && cursor.getType() == Material.LAVA_BUCKET && isItem(current) && isDestroyable(current)) {
            event.setCancelled(true);
            event.setCurrentItem(decrementOne(current));
            return true;
        }
        return false;
    }

    private boolean isRightClick(ClickType clickType) {
        return clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT;
    }

    private boolean isDestroyable(ItemStack item) {
        return isItem(item) && !isShulkerBox(item) && !isFireImmune(item.getType());
    }

    private boolean isArmor(ItemStack item) {
        if (!isItem(item)) {
            return false;
        }
        return switch (item.getType().getEquipmentSlot()) {
            case HEAD, CHEST, LEGS, FEET -> true;
            default -> false;
        };
    }

    private boolean isShulkerBox(ItemStack item) {
        return isItem(item) && item.getType().name().endsWith("SHULKER_BOX");
    }

    private boolean isFireImmune(Material material) {
        String name = material.name();
        return name.startsWith("NETHERITE_")
                || material == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                || material == Material.NETHER_STAR
                || material == Material.ANCIENT_DEBRIS;
    }

    private boolean isItem(ItemStack item) {
        return item != null && item.getType().isItem() && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private ItemStack decrementOne(ItemStack source) {
        if (!isItem(source)) {
            return null;
        }
        int amount = source.getAmount();
        if (amount <= 1) {
            return null;
        }
        ItemStack copy = source.clone();
        copy.setAmount(amount - 1);
        return copy;
    }
}
