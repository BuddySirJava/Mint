package ir.buddy.mint.module.impl.building;

import org.bukkit.plugin.java.JavaPlugin;
import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MixedSlabModule implements Module, Listener {

    private final MintPlugin plugin;
    private boolean registered = false;


    private static NamespacedKey keyMixedSlabStatic;
    private static final NamespacedKey LEGACY_MIXED_SLAB_MARKER =
            new NamespacedKey("bukkuark", "mixed_slab");
    private static final NamespacedKey LEGACY_SLAB_HALF = new NamespacedKey("bukkuark", "slab_half");

    private NamespacedKey keyMixedSlab;
    private NamespacedKey keySlabHalf;
    private NamespacedKey keyPartnerMaterial;


    private final Map<UUID, MiningContext> activeMining = new ConcurrentHashMap<>();

    public MixedSlabModule(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Mixed Slab";
    }

    @Override
    public String getConfigPath() {
        return "modules.mixedslab";
    }

    @Override
    public String getDescription() {
        return "Place two different slabs in one block.";
    }

    @Override
    public void enable() {
        keyMixedSlab = new NamespacedKey(plugin, "mixed_slab");
        keyMixedSlabStatic = keyMixedSlab;
        keySlabHalf = new NamespacedKey(plugin, "slab_half");
        keyPartnerMaterial = new NamespacedKey(plugin, "partner_material");

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
    public void onCreativePickBlock(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof BlockDisplay)) return;

        BlockDisplay display = (BlockDisplay) entity;
        if (!isMixedSlabDisplay(display)) return;


        Material slabMaterial = display.getBlock().getMaterial();


        PlayerInventory inv = player.getInventory();
        ItemStack slabItem = new ItemStack(slabMaterial, 1);


        int heldSlot = inv.getHeldItemSlot();
        inv.setItem(heldSlot, slabItem);

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isSlabItem(hand)) return;

        Material placingMat = hand.getType();
        BlockFace face = event.getBlockFace();

        Block targetSlab = null;

        if (clicked.getBlockData() instanceof Slab clickedSlab && clickedSlab.getType() != Slab.Type.DOUBLE) {
            if (clickedSlab.getType() == Slab.Type.BOTTOM && face != BlockFace.DOWN) {
                targetSlab = clicked;
            } else if (clickedSlab.getType() == Slab.Type.TOP && face != BlockFace.UP) {
                targetSlab = clicked;
            }
        }


        if (clicked.getBlockData() instanceof TrapDoor trapdoor) {


            if (trapdoor.isOpen()) {
                BlockFace trapdoorFacing = trapdoor.getFacing();
                if (face == trapdoorFacing || face == trapdoorFacing.getOppositeFace()) {
                    return;
                }
            }
        }

        if (targetSlab == null) {
            Block relative = clicked.getRelative(face);


            if (relative.getBlockData() instanceof TrapDoor) {
                return;
            }

            if (relative.getBlockData() instanceof Slab relativeSlab && relativeSlab.getType() != Slab.Type.DOUBLE) {
                if (relativeSlab.getType() == Slab.Type.BOTTOM && face == BlockFace.DOWN) {
                } else if (relativeSlab.getType() == Slab.Type.TOP && face == BlockFace.UP) {
                    targetSlab = relative;
                } else if (face != BlockFace.UP && face != BlockFace.DOWN) {
                }
            }
        }

        if (targetSlab == null) {
            return;
        }

        if (!ModuleAccess.canBuild(plugin, player, targetSlab.getLocation())) return;

        Material targetMat = targetSlab.getType();

        if (placingMat == targetMat) return;

        event.setCancelled(true);

        Slab targetSlabData = (Slab) targetSlab.getBlockData();
        Material bottomMat = (targetSlabData.getType() == Slab.Type.BOTTOM) ? targetMat : placingMat;
        Material topMat = (targetSlabData.getType() == Slab.Type.TOP) ? targetMat : placingMat;

        createMixedSlab(targetSlab, bottomMat, topMat, player);

        player.swingMainHand();
    }


    private void createMixedSlab(Block block, Material bottomMat, Material topMat, Player player) {
        Location loc = block.getLocation();
        World world = block.getWorld();


        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
        }


        block.setType(Material.BARRIER, false);


        spawnSlabDisplay(loc, bottomMat, "bottom");


        spawnSlabDisplay(loc, topMat, "top");


        world.playSound(loc, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
    }

    private void spawnSlabDisplay(Location blockLoc, Material slabMat, String half) {
        World world = blockLoc.getWorld();
        Location spawnLoc = blockLoc.clone().add(0, 0, 0);

        world.spawn(spawnLoc, BlockDisplay.class, display -> {

            BlockData data = Bukkit.createBlockData(slabMat, bd -> {
                if (bd instanceof Slab slab) {
                    slab.setType(half.equals("top") ? Slab.Type.TOP : Slab.Type.BOTTOM);
                }
            });
            display.setBlock(data);


            float overscale = 1.001f;
            float overscaleOffset = -(overscale - 1.0f) / 2.0f;
            Transformation t = new Transformation(
                    new Vector3f(overscaleOffset, overscaleOffset, overscaleOffset),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(overscale, overscale, overscale),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            display.setTransformation(t);


            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(keyMixedSlab, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keySlabHalf, PersistentDataType.STRING, half);
            pdc.set(keyPartnerMaterial, PersistentDataType.STRING,
                    half.equals("top") ? "" : "");

            display.setPersistent(true);
            display.setGravity(false);
        });
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER) return;
        if (!isMixedSlabAt(block)) return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;
        if (!ModuleAccess.canBuild(plugin, player, block.getLocation())) return;


        String targetHalf = getTargetHalf(player, block);


        BlockDisplay targetDisplay = findDisplay(block, targetHalf);
        if (targetDisplay == null) return;


        Material slabMat = targetDisplay.getBlock().getMaterial();


        BlockData slabData = Bukkit.createBlockData(slabMat, bd -> {
            if (bd instanceof Slab slab) {
                slab.setType(targetHalf.equals("top") ? Slab.Type.TOP : Slab.Type.BOTTOM);
            }
        });
        block.setBlockData(slabData, false);


        targetDisplay.setVisibleByDefault(false);


        activeMining.put(player.getUniqueId(), new MiningContext(
                block.getLocation(), targetHalf, slabMat, targetDisplay
        ));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        MiningContext ctx = activeMining.remove(event.getPlayer().getUniqueId());
        if (ctx == null) return;

        restoreBarrier(ctx);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        MiningContext ctx = activeMining.remove(event.getPlayer().getUniqueId());
        if (ctx == null) return;

        restoreBarrier(ctx);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        boolean moduleEnabledForPlayer = ModuleAccess.isEnabledForPlayer(plugin, this, player);
        MiningContext ctx = activeMining.remove(player.getUniqueId());

        Block block = event.getBlock();
        if (!moduleEnabledForPlayer) {
            if (block.getType() == Material.BARRIER && isMixedSlabAt(block)) {
                cleanupMixedSlab(block, false);
            }
            return;
        }
        if (!ModuleAccess.canBuild(plugin, player, block.getLocation())) return;


        if (ctx != null && ctx.location.equals(block.getLocation())) {
            event.setCancelled(true);
            event.setDropItems(false);

            processHalfBreak(block, player, ctx.half, ctx.slabMaterial, ctx.display);
            return;
        }


        if (block.getType() == Material.BARRIER && isMixedSlabAt(block)) {
            event.setCancelled(true);
            event.setDropItems(false);

            String targetHalf = getTargetHalf(player, block);
            BlockDisplay targetDisplay = findDisplay(block, targetHalf);

            if (targetDisplay != null) {
                Material slabMat = targetDisplay.getBlock().getMaterial();
                processHalfBreak(block, player, targetHalf, slabMat, targetDisplay);
            } else {

                cleanupMixedSlab(block, player.getGameMode() != GameMode.CREATIVE);
                block.setType(Material.AIR);
            }
        }
    }

    private void processHalfBreak(Block block, Player player, String targetHalf, Material slabMat, BlockDisplay targetDisplay) {

        if (player.getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(slabMat, 1)
            );
        }


        if (targetDisplay != null && !targetDisplay.isDead()) {
            targetDisplay.remove();
        }


        String otherHalf = targetHalf.equals("top") ? "bottom" : "top";
        BlockDisplay remaining = findDisplay(block, otherHalf);

        if (remaining != null) {

            Material remainingMat = remaining.getBlock().getMaterial();
            BlockData remainingData = Bukkit.createBlockData(remainingMat, bd -> {
                if (bd instanceof Slab slab) {
                    slab.setType(otherHalf.equals("top") ? Slab.Type.TOP : Slab.Type.BOTTOM);
                }
            });
            block.setBlockData(remainingData, true);
            remaining.remove();
        } else {

            block.setType(Material.AIR);
        }


        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                20, 0.3, 0.3, 0.3, 0.05, Bukkit.createBlockData(slabMat));
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (block.getType() == Material.BARRIER && isMixedSlabAt(block)) {
                it.remove();
                cleanupMixedSlab(block, true);
                block.setType(Material.AIR);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (block.getType() == Material.BARRIER && isMixedSlabAt(block)) {
                it.remove();
                cleanupMixedSlab(block, true);
                block.setType(Material.AIR);
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (block.getType() == Material.BARRIER && isMixedSlabAt(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (block.getType() == Material.BARRIER && isMixedSlabAt(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {

        Block against = event.getBlockAgainst();
        if (against.getType() == Material.BARRIER && isMixedSlabAt(against)) {

            if (event.getBlock().getLocation().equals(against.getLocation())) {
                event.setCancelled(true);
            }
        }
    }


    private boolean isSlabItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return item.getType().name().endsWith("_SLAB");
    }

    private boolean isMixedSlabAt(Block block) {
        return findDisplay(block, "bottom") != null || findDisplay(block, "top") != null;
    }


    public static boolean isMixedSlabBlock(JavaPlugin plugin, Block block) {
        if (block == null || keyMixedSlabStatic == null) return false;
        if (block.getType() != Material.BARRIER) return false;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (!(entity instanceof BlockDisplay bd)) continue;
            PersistentDataContainer pdc = bd.getPersistentDataContainer();
            if (!pdc.has(keyMixedSlabStatic, PersistentDataType.BYTE)
                    && !pdc.has(LEGACY_MIXED_SLAB_MARKER, PersistentDataType.BYTE)) {
                continue;
            }
            Location eLoc = entity.getLocation();
            if (eLoc.getBlockX() == block.getX()
                    && eLoc.getBlockY() == block.getY()
                    && eLoc.getBlockZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    private boolean isMixedSlabDisplay(BlockDisplay bd) {
        PersistentDataContainer pdc = bd.getPersistentDataContainer();
        return pdc.has(keyMixedSlab, PersistentDataType.BYTE)
                || pdc.has(LEGACY_MIXED_SLAB_MARKER, PersistentDataType.BYTE);
    }

    private BlockDisplay findDisplay(Block block, String half) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (!(entity instanceof BlockDisplay bd)) continue;
            PersistentDataContainer pdc = bd.getPersistentDataContainer();
            if (!isMixedSlabDisplay(bd)) {
                continue;
            }
            String h = pdc.has(keySlabHalf, PersistentDataType.STRING)
                    ? pdc.get(keySlabHalf, PersistentDataType.STRING)
                    : pdc.get(LEGACY_SLAB_HALF, PersistentDataType.STRING);
            if (half.equals(h)) {

                Location eLoc = entity.getLocation();
                if (eLoc.getBlockX() == block.getX()
                        && eLoc.getBlockY() == block.getY()
                        && eLoc.getBlockZ() == block.getZ()) {
                    return bd;
                }
            }
        }
        return null;
    }

    private List<BlockDisplay> findAllDisplays(Block block) {
        List<BlockDisplay> displays = new ArrayList<>();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (!(entity instanceof BlockDisplay bd)) continue;
            if (!isMixedSlabDisplay(bd)) continue;
            Location eLoc = entity.getLocation();
            if (eLoc.getBlockX() == block.getX()
                    && eLoc.getBlockY() == block.getY()
                    && eLoc.getBlockZ() == block.getZ()) {
                displays.add(bd);
            }
        }
        return displays;
    }

    private String getTargetHalf(Player player, Block block) {

        Location eyeLoc = player.getEyeLocation();
        var rayResult = player.rayTraceBlocks(5.0);
        if (rayResult != null && rayResult.getHitPosition() != null) {
            double hitY = rayResult.getHitPosition().getY();
            double blockY = block.getY();
            double relativeY = hitY - blockY;
            return relativeY > 0.5 ? "top" : "bottom";
        }

        return eyeLoc.getY() > block.getY() + 0.5 ? "top" : "bottom";
    }

    private void restoreBarrier(MiningContext ctx) {
        Block block = ctx.location.getBlock();

        if (block.getType() == ctx.slabMaterial) {
            block.setType(Material.BARRIER, false);
        }

        if (ctx.display != null && !ctx.display.isDead()) {
            ctx.display.setVisibleByDefault(true);
        }
    }

    private void cleanupMixedSlab(Block block, boolean dropItems) {
        List<BlockDisplay> displays = findAllDisplays(block);
        for (BlockDisplay bd : displays) {
            if (dropItems) {
                Material mat = bd.getBlock().getMaterial();
                block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(mat, 1)
                );
            }
            bd.remove();
        }
    }


    private static class MiningContext {
        final Location location;
        final String half;
        final Material slabMaterial;
        final BlockDisplay display;

        MiningContext(Location location, String half, Material slabMaterial, BlockDisplay display) {
            this.location = location;
            this.half = half;
            this.slabMaterial = slabMaterial;
            this.display = display;
        }
    }
}
