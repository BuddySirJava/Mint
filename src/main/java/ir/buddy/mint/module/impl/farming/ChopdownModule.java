package ir.buddy.mint.module.impl.farming;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ChopdownModule implements Module, Listener {

    private final JavaPlugin plugin;

    public ChopdownModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Chopdown";
    }

    @Override
    public String getConfigPath() {
        return "modules.chopdown";
    }

    @Override
    public String getDescription() {
        return "Drops trees with opposite-side fall and vanilla-like leaf loot handling.";
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
    public void onTreeLogBreak(BlockBreakEvent event) {
        Block origin = event.getBlock();
        if (!isLog(origin.getType())) {
            return;
        }

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            return;
        }
        if (!ModuleAccess.canBuild(plugin, player, origin.getLocation())) {
            return;
        }

        int dirX = clamp(origin.getX() - (int) Math.round(player.getLocation().getX() - 0.5), -1, 1);
        int dirZ = clamp(origin.getZ() - (int) Math.round(player.getLocation().getZ() - 0.5), -1, 1);

        if (FoliaScheduler.isFolia()) {
            if (processFoliaSafeColumn(player, origin, event, dirX, dirZ)) {
                return;
            }
            return;
        }

        TreeSelection tree = detectTree(origin, getMaxBlocks(), getSearchRadius(), getLeafSteps(), getMaxHorizontalOffset());
        if (tree.logs().size() < getMinLogs() || tree.leaves().size() < getMinLeaves()) {
            return;
        }
        if (!hasVerticalTrunk(origin, tree.logs(), getTrunkMinHeight())) {
            return;
        }
        if (!hasCanopyAboveOrigin(origin, tree.logs(), tree.leaves())) {
            return;
        }

        event.setCancelled(true);
        playChopdownSounds(origin.getLocation(), tree.logs().size(), tree.leaves().size());
        processTree(player, origin, tree, dirX, dirZ);
    }

    private boolean processFoliaSafeColumn(Player player, Block origin, BlockBreakEvent event, int dirX, int dirZ) {
        Set<Block> trunk = new HashSet<>();
        Set<Block> leaves = new HashSet<>();
        int minWorldY = origin.getWorld().getMinHeight();
        int maxWorldY = origin.getWorld().getMaxHeight();

        int baseY = origin.getY();
        while (baseY > minWorldY) {
            Block below = origin.getWorld().getBlockAt(origin.getX(), baseY - 1, origin.getZ());
            if (!isLog(below.getType())) {
                break;
            }
            baseY--;
        }

        int maxHeight = Math.max(getTrunkMinHeight() + 14, getMinLogs() + 6);
        int maxY = Math.min(maxWorldY - 1, baseY + maxHeight);
        for (int y = baseY; y <= maxY; y++) {
            Block check = origin.getWorld().getBlockAt(origin.getX(), y, origin.getZ());
            if (!isLog(check.getType())) {
                break;
            }
            trunk.add(check);
        }

        if (trunk.size() < getMinLogs()) {
            return false;
        }

        int leafRadius = Math.max(2, getMaxHorizontalOffset());
        int minLeafY = Math.max(minWorldY, baseY);
        int maxLeafY = Math.min(maxWorldY - 1, maxY + getLeafSteps() + 2);
        for (int y = minLeafY; y <= maxLeafY; y++) {
            for (int x = origin.getX() - leafRadius; x <= origin.getX() + leafRadius; x++) {
                for (int z = origin.getZ() - leafRadius; z <= origin.getZ() + leafRadius; z++) {
                    Block check = origin.getWorld().getBlockAt(x, y, z);
                    if (!isLeaf(check.getType())) {
                        continue;
                    }
                    if (horizontalDistanceSquared(check, origin) > leafRadius * leafRadius) {
                        continue;
                    }
                    leaves.add(check);
                }
            }
        }

        for (Block log : trunk) {
            if (!ModuleAccess.canBuild(plugin, player, log.getLocation())) {
                return false;
            }
        }
        for (Block leaf : leaves) {
            if (!ModuleAccess.canBuild(plugin, player, leaf.getLocation())) {
                return false;
            }
        }

        event.setCancelled(true);
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Block log : trunk) {
            if (!isDraggable(log.getRelative(BlockFace.DOWN))) {
                continue;
            }
            int oy = log.getY() - origin.getY();
            Block target = log.getRelative(oy * dirX, 0, oy * dirZ);
            if (!canAffectTreeBlock(player, log, target)) {
                continue;
            }
            spawnFalling(log, target);
        }
        for (Block leaf : leaves) {
            if (!isDraggable(leaf.getRelative(BlockFace.DOWN))) {
                continue;
            }
            int oy = leaf.getY() - origin.getY();
            Block target = leaf.getRelative(oy * dirX, 0, oy * dirZ);
            if (!canAffectTreeBlock(player, leaf, target)) {
                continue;
            }
            spawnDrops(leaf, leaf.getDrops(tool, player));
            spawnFalling(leaf, target);
        }
        playChopdownSounds(origin.getLocation(), trunk.size(), leaves.size());
        return true;
    }

    private void processTree(Player player, Block origin, TreeSelection tree, int dirX, int dirZ) {
        ItemStack tool = player.getInventory().getItemInMainHand();

        for (Block log : tree.logs()) {
            if (!isDraggable(log.getRelative(BlockFace.DOWN))) {
                continue;
            }
            int oy = log.getY() - origin.getY();
            Block target = log.getRelative(oy * dirX, 0, oy * dirZ);
            if (!canAffectTreeBlock(player, log, target)) {
                continue;
            }
            spawnFalling(log, target);
        }
        for (Block leaf : tree.leaves()) {
            if (!isDraggable(leaf.getRelative(BlockFace.DOWN))) {
                continue;
            }
            int oy = leaf.getY() - origin.getY();
            Block target = leaf.getRelative(oy * dirX, 0, oy * dirZ);
            if (!canAffectTreeBlock(player, leaf, target)) {
                continue;
            }
            
            spawnDrops(leaf, leaf.getDrops(tool, player));
            spawnFalling(leaf, target);
        }
    }

    private boolean canAffectTreeBlock(Player player, Block source, Block target) {
        return ModuleAccess.canBuild(plugin, player, source.getLocation())
                && ModuleAccess.canBuild(plugin, player, target.getLocation());
    }

    private void spawnFalling(Block block, Block target) {
        Material material = block.getType();
        BlockData data = block.getBlockData().clone();
        Location spawnLocation = target.getLocation().add(0.5, 0.0, 0.5);

        block.setType(Material.AIR, false);

        FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(spawnLocation, data);
        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(false);
        fallingBlock.setVelocity(new Vector(0, 0.02, 0));
        if (!isLog(material) && !isLeaf(material)) {
            fallingBlock.remove();
        }
    }

    private TreeSelection detectTree(Block origin, int maxBlocks, int searchRadius, int leafSteps, int maxHorizontalOffset) {
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Map<Block, Integer> used = new HashMap<>();
        Set<Block> logs = new HashSet<>();
        Set<Block> leaves = new HashSet<>();

        queue.add(origin);
        used.put(origin, leafSteps);

        while (!queue.isEmpty() && used.size() < maxBlocks) {
            Block top = queue.pollFirst();
            int topStep = used.getOrDefault(top, 0);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block neighbor = top.getRelative(dx, dy, dz);
                        if (topStep <= 0 || squaredDistance(neighbor, origin) > searchRadius * searchRadius) {
                            continue;
                        }
                        if (horizontalDistanceSquared(neighbor, origin) > maxHorizontalOffset * maxHorizontalOffset) {
                            continue;
                        }

                        Material neighborType = neighbor.getType();
                        boolean log = isLog(neighborType);
                        boolean leaf = isLeaf(neighborType);

                        if ((dy >= 0 && topStep == leafSteps && log) || leaf) {
                            int nextStep = leaf ? topStep - 1 : topStep;
                            int previousStep = used.getOrDefault(neighbor, Integer.MIN_VALUE);
                            if (nextStep > previousStep) {
                                used.put(neighbor, nextStep);
                                queue.push(neighbor);
                            }
                        }
                    }
                }
            }
        }

        for (Block block : used.keySet()) {
            if (isLog(block.getType())) {
                logs.add(block);
            } else if (isLeaf(block.getType())) {
                leaves.add(block);
            }
        }

        return new TreeSelection(logs, leaves);
    }

    private boolean hasVerticalTrunk(Block origin, Set<Block> logs, int minHeight) {
        int contiguous = 0;
        for (int y = origin.getY(); y <= origin.getY() + minHeight + 6; y++) {
            Block check = origin.getWorld().getBlockAt(origin.getX(), y, origin.getZ());
            if (logs.contains(check) && isLog(check.getType())) {
                contiguous++;
                if (contiguous >= minHeight) {
                    return true;
                }
            } else if (contiguous > 0) {
                break;
            }
        }
        return false;
    }

    private boolean hasCanopyAboveOrigin(Block origin, Set<Block> logs, Set<Block> leaves) {
        for (Block leaf : leaves) {
            if (leaf.getY() <= origin.getY()) {
                continue;
            }
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP}) {
                Block near = leaf.getRelative(face);
                if (near.getY() >= origin.getY() && logs.contains(near)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void spawnDrops(Block block, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
                continue;
            }
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.3, 0.5), drop);
        }
    }

    private int squaredDistance(Block a, Block b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private int horizontalDistanceSquared(Block a, Block b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material);
    }

    private boolean isLeaf(Material material) {
        return Tag.LEAVES.isTagged(material);
    }

    private boolean isDraggable(Block block) {
        Material type = block.getType();
        return isLog(type) || isLeaf(type) || type.isAir() || block.isPassable();
    }

    private void playChopdownSounds(Location origin, int logCount, int leafCount) {
        float baseVolume = (float) getSoundVolume();
        float chopPitch = (float) getSoundPitch();
        origin.getWorld().playSound(origin, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, baseVolume, chopPitch);

        long delayTicks = Math.min(8L, Math.max(2L, logCount / 6L));
        float rustleVolume = Math.min(2.0f, baseVolume + (leafCount > 12 ? 0.2f : 0.0f));
        float rustlePitch = Math.max(0.6f, chopPitch - 0.15f);
        FoliaScheduler.runRegionLater(plugin, origin, delayTicks, () -> {
            if (!plugin.isEnabled() || origin.getWorld() == null) {
                return;
            }
            origin.getWorld().playSound(origin, Sound.BLOCK_AZALEA_LEAVES_BREAK, SoundCategory.BLOCKS, rustleVolume, rustlePitch);
        });
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int getMinLogs() {
        return Math.max(1, plugin.getConfig().getInt(getConfigPath() + ".min-logs", 3));
    }

    private int getMinLeaves() {
        return Math.max(0, plugin.getConfig().getInt(getConfigPath() + ".min-leaves", 5));
    }

    private int getMaxBlocks() {
        return Math.max(16, plugin.getConfig().getInt(getConfigPath() + ".max-blocks", 384));
    }

    private int getSearchRadius() {
        return Math.max(4, plugin.getConfig().getInt(getConfigPath() + ".search-radius", 10));
    }

    private int getLeafSteps() {
        return Math.max(2, plugin.getConfig().getInt(getConfigPath() + ".leaf-steps", 4));
    }

    private int getTrunkMinHeight() {
        return Math.max(2, plugin.getConfig().getInt(getConfigPath() + ".trunk-min-height", 3));
    }

    private int getMaxHorizontalOffset() {
        return Math.max(2, plugin.getConfig().getInt(getConfigPath() + ".max-horizontal-offset", 6));
    }

    private double getSoundVolume() {
        return clampDouble(plugin.getConfig().getDouble(getConfigPath() + ".sound.volume", 1.15), 0.0, 2.0);
    }

    private double getSoundPitch() {
        return clampDouble(plugin.getConfig().getDouble(getConfigPath() + ".sound.pitch", 0.9), 0.5, 2.0);
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TreeSelection(Set<Block> logs, Set<Block> leaves) {
    }
}
