package ir.buddy.mint.module.support;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModuleEventGuards {

    private ModuleEventGuards() {
    }

    public static boolean isMainHand(EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND;
    }

    public static boolean isRightClickBlock(Action action) {
        return action == Action.RIGHT_CLICK_BLOCK;
    }

    public static boolean isEnabledForPlayerAndCanBuild(JavaPlugin plugin, Module module, Player player, Location location) {
        return ModuleAccess.isEnabledForPlayer(plugin, module, player) && ModuleAccess.canBuild(plugin, player, location);
    }

    public static ItemStack heldItem(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }
}
