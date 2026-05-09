package ir.buddy.mint.module.impl.interaction;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;

import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class DoubleDoorModule implements Module, Listener {

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final MintPlugin plugin;
    private boolean registered = false;

    public DoubleDoorModule(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Double Door";
    }

    @Override
    public String getConfigPath() {
        return "modules.doubledoor";
    }

    @Override
    public String getDescription() {
        return "Open paired double doors together.";
    }

    @Override
    public void enable() {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        registered = false;
    }

    



    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDoorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) {
            return;
        }
        if (event.getPlayer().isSneaking() && event.getItem() != null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !Tag.DOORS.isTagged(block.getType())) {
            return;
        }
        if (!ModuleAccess.canBuild(plugin, event.getPlayer(), block.getLocation())) {
            return;
        }

        BlockData data = block.getBlockData();
        if (!(data instanceof Door door)) {
            return;
        }

        Block bottom = door.getHalf() == Door.Half.TOP
                ? block.getRelative(BlockFace.DOWN)
                : block;

        if (!(bottom.getBlockData() instanceof Door bottomDoor)) {
            return;
        }
        Block top = bottom.getRelative(BlockFace.UP);
        if (!ModuleAccess.canBuild(plugin, event.getPlayer(), bottom.getLocation())
                || !ModuleAccess.canBuild(plugin, event.getPlayer(), top.getLocation())) {
            return;
        }

        boolean newState = !bottomDoor.isOpen();

        for (BlockFace face : HORIZONTAL) {
            Block adj = bottom.getRelative(face);
            if (adj.getType() != bottom.getType()) {
                continue;
            }
            if (!(adj.getBlockData() instanceof Door adjDoor)) {
                continue;
            }
            if (adjDoor.getHalf() != Door.Half.BOTTOM) {
                continue;
            }
            Block adjTop = adj.getRelative(BlockFace.UP);
            if (!ModuleAccess.canBuild(plugin, event.getPlayer(), adj.getLocation())
                    || !ModuleAccess.canBuild(plugin, event.getPlayer(), adjTop.getLocation())) {
                continue;
            }

            adjDoor.setOpen(newState);
            adj.setBlockData(adjDoor, false);
            if (adjTop.getBlockData() instanceof Door adjTopDoor) {
                adjTopDoor.setOpen(newState);
                adjTop.setBlockData(adjTopDoor, false);
            }
        }

        bottomDoor.setOpen(newState);
        bottom.setBlockData(bottomDoor, false);

        if (top.getBlockData() instanceof Door topDoor) {
            topDoor.setOpen(newState);
            top.setBlockData(topDoor, false);
        }
    }
}
