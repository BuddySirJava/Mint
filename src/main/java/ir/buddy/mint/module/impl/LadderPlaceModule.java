package ir.buddy.mint.module.impl;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class LadderPlaceModule implements Module, Listener {

    private final JavaPlugin plugin;

    public LadderPlaceModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Ladder Place";
    }

    @Override
    public String getConfigPath() {
        return "modules.ladder-place";
    }

    @Override
    public String getDescription() {
        return "Place ladders quickly above or below.";
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (!event.getPlayer().isSneaking())
            return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer()))
            return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.LADDER)
            return;

        ItemStack itemInHand = event.getPlayer().getInventory().getItemInMainHand();
        if (itemInHand.getType() != Material.LADDER)
            return;

        BlockFace existingFacing = ((Directional) clickedBlock.getBlockData()).getFacing();

        boolean lookingDown = event.getPlayer().getLocation().getPitch() > 0;
        Block target = findPlaceableSpot(clickedBlock, existingFacing, lookingDown);
        if (target == null)
            return;
        if (!ModuleAccess.canBuild(plugin, event.getPlayer(), target.getLocation()))
            return;


        target.setType(Material.LADDER);
        Directional ladderData = (Directional) target.getBlockData();
        ladderData.setFacing(existingFacing);
        target.setBlockData(ladderData);

        event.getPlayer().swingMainHand();
        target.getWorld().playSound(
                event.getPlayer().getLocation(),
                Sound.BLOCK_WOOD_PLACE,
                1.0f, 1.0f);

        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        }

        event.setCancelled(true);
    }

    private Block findPlaceableSpot(Block origin, BlockFace facing, boolean downFirst) {
        BlockFace primary = downFirst ? BlockFace.DOWN : BlockFace.UP;
        BlockFace secondary = downFirst ? BlockFace.UP : BlockFace.DOWN;

        Block candidate = scanDirection(origin, facing, primary);
        if (candidate != null)
            return candidate;

        return scanDirection(origin, facing, secondary);
    }

    private Block scanDirection(Block origin, BlockFace facing, BlockFace direction) {
        Block candidate = origin.getRelative(direction);
        while (candidate.getType() == Material.LADDER) {
            if (((Directional) candidate.getBlockData()).getFacing() != facing)
                break;
            candidate = candidate.getRelative(direction);
        }
        if (candidate.getType() == Material.AIR || candidate.getType() == Material.CAVE_AIR) {
            return candidate;
        }
        return null;
    }
}
