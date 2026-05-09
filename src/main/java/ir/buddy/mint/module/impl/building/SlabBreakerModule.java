package ir.buddy.mint.module.impl.building;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

public class SlabBreakerModule implements Module, Listener {

    private final JavaPlugin plugin;

    public SlabBreakerModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Slab Breaker";
    }

    @Override
    public String getConfigPath() {
        return "modules.slab-breaker";
    }

    @Override
    public String getDescription() {
        return "Break only one half of double slabs.";
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
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;
        if (!player.isSneaking()) return;
        if (!ModuleAccess.canBuild(plugin, player, event.getBlock().getLocation())) return;
        Block block = event.getBlock();
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Slab)) return;

        Slab slab = (Slab) blockData;
        if (slab.getType() != Slab.Type.DOUBLE) return;

        event.setCancelled(true);

        Slab.Type targetHalf = getTargetHalf(player, block);


        if (targetHalf == Slab.Type.TOP) {
            slab.setType(Slab.Type.BOTTOM);
        } else {
            slab.setType(Slab.Type.TOP);
        }

        block.setBlockData(slab);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
                Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);


        if (player.getGameMode() != GameMode.CREATIVE) {
            Material slabMaterial = block.getType();
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(slabMaterial, 1)
            );
        }


        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (tool.getType() != Material.AIR && tool.getItemMeta() != null) {
                tool.damage(1, player);
            }
        }
    }

    private Slab.Type getTargetHalf(Player player, Block block) {

        RayTraceResult result = player.rayTraceBlocks(6.0);

        if (result != null && result.getHitPosition() != null) {
            double hitY = result.getHitPosition().getY();
            double blockY = block.getY();
            double relativeY = hitY - blockY;


            if (relativeY > 0.5) {
                return Slab.Type.TOP;
            }

            if (relativeY == 0.5) {
                return player.getLocation().getPitch() > 0 ? Slab.Type.TOP : Slab.Type.BOTTOM;
            }
            return Slab.Type.BOTTOM;
        }


        return player.getLocation().getPitch() > 0 ? Slab.Type.TOP : Slab.Type.BOTTOM;
    }
}
