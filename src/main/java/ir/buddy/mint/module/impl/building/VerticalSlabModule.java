package ir.buddy.mint.module.impl.building;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.List;

public class VerticalSlabModule implements Module, Listener {

    private final MintPlugin plugin;
    private boolean registered = false;

    private NamespacedKey keyVerticalSlab;
    private NamespacedKey keySide;
    private NamespacedKey keyMaterial;

    private static final String LEGACY_PLUGIN_NAMESPACE = "bukkuark";
    private NamespacedKey legacyVerticalSlab;
    private NamespacedKey legacySide;
    private NamespacedKey legacyMaterial;

    private Transformation southTransform;
    private Transformation westTransform;
    private Transformation northTransform;
    private Transformation eastTransform;


    private final Map<UUID, MiningContext> activeMining = new ConcurrentHashMap<>();

    public VerticalSlabModule(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Vertical Slab";
    }

    @Override
    public String getConfigPath() {
        return "modules.verticalslab";
    }

    @Override
    public String getDescription() {
        return "Enable vertical slab placement visuals.";
    }

    @Override
    public void enable() {
        keyVerticalSlab = new NamespacedKey(plugin, "vertical_slab");
        keySide = new NamespacedKey(plugin, "vertical_side");
        keyMaterial = new NamespacedKey(plugin, "vertical_material");
        legacyVerticalSlab = new NamespacedKey(LEGACY_PLUGIN_NAMESPACE, "vertical_slab");
        legacySide = new NamespacedKey(LEGACY_PLUGIN_NAMESPACE, "vertical_side");
        legacyMaterial = new NamespacedKey(LEGACY_PLUGIN_NAMESPACE, "vertical_material");
        initializeTransformations();

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

    private boolean hasVerticalSlabMarker(PersistentDataContainer pdc) {
        return pdc.has(keyVerticalSlab, PersistentDataType.BYTE)
                || pdc.has(legacyVerticalSlab, PersistentDataType.BYTE);
    }

    private String getVerticalSideString(PersistentDataContainer pdc) {
        if (pdc.has(keySide, PersistentDataType.STRING)) {
            return pdc.get(keySide, PersistentDataType.STRING);
        }
        if (pdc.has(legacySide, PersistentDataType.STRING)) {
            return pdc.get(legacySide, PersistentDataType.STRING);
        }
        return null;
    }

    private String getVerticalMaterialName(PersistentDataContainer pdc) {
        if (pdc.has(keyMaterial, PersistentDataType.STRING)) {
            return pdc.get(keyMaterial, PersistentDataType.STRING);
        }
        if (pdc.has(legacyMaterial, PersistentDataType.STRING)) {
            return pdc.get(legacyMaterial, PersistentDataType.STRING);
        }
        return null;
    }

    private void initializeTransformations() {
        southTransform = createTransformation(-0.002f, 1.002f, 0.5f, 0.7071068f, 0.0f, 0.0f, 0.7071068f);
        westTransform = createTransformation(0.5f, 0.002f, -0.002f, 0.0f, 0.0f, 0.7071068f, 0.7071068f);
        northTransform = createTransformation(-0.002f, 1.002f, 0.0f, 0.7071068f, 0.0f, 0.0f, 0.7071068f);
        eastTransform = createTransformation(0.5f, 1.002f, -0.002f, 0.0f, 0.0f, -0.7071068f, 0.7071068f);
    }

    private Transformation createTransformation(float tx, float ty, float tz, float rx, float ry, float rz, float rw) {
        return new Transformation(
                new Vector3f(tx, ty, tz),
                new Quaternionf(rx, ry, rz, rw),
                new Vector3f(1.01f, 1.01f, 1.01f),
                new Quaternionf(0.0f, 0.0f, 0.0f, 1.0f)
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreativePickBlock(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof BlockDisplay)) return;

        BlockDisplay display = (BlockDisplay) entity;
        if (!isVerticalSlabDisplay(display)) return;


        PersistentDataContainer pdc = display.getPersistentDataContainer();
        String materialName = getVerticalMaterialName(pdc);
        if (materialName == null) return;

        Material slabMaterial = Material.matchMaterial(materialName);
        if (slabMaterial == null) return;


        PlayerInventory inv = player.getInventory();
        ItemStack slabItem = new ItemStack(slabMaterial, 1);


        int heldSlot = inv.getHeldItemSlot();
        inv.setItem(heldSlot, slabItem);

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTrapdoorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;


        if (clicked.getType() == Material.OAK_TRAPDOOR && isVerticalSlabAt(clicked)) {
            Player player = event.getPlayer();


            if (!ModuleAccess.canBuild(plugin, player, clicked.getLocation())) return;

            ItemStack hand = player.getInventory().getItemInMainHand();

            if (!isSlabItem(hand)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        boolean isVertical = (clicked.getType() == Material.BARRIER || clicked.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(clicked);


        if (!player.isSneaking() && !isVertical) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isSlabItem(hand)) return;

        BlockFace face = event.getBlockFace();
        Material placeMat = hand.getType();
        Block target;
        String side;

        if (isVertical) {
            if (clicked.getType() == Material.OAK_TRAPDOOR) {

                List<BlockDisplay> displays = findAllDisplays(clicked);
                if (displays.isEmpty()) return;

                String existingSide = getVerticalSideString(displays.get(0).getPersistentDataContainer());
                side = getOppositeSideStr(existingSide);
                target = clicked;
            } else {

                if (!isHorizontal(face)) return;
                target = clicked.getRelative(face);
                side = face.getOppositeFace().name().toLowerCase();
            }
        } else {

            if (!isHorizontal(face)) return;
            target = clicked;
            side = face.name().toLowerCase();

            if (!canUseAsVerticalTarget(target)) {
                Block relative = clicked.getRelative(face);
                if (canUseAsVerticalTarget(relative)) {
                    target = relative;
                    side = face.getOppositeFace().name().toLowerCase();
                } else {
                    return;
                }
            }
        }

        if (!ModuleAccess.canBuild(plugin, player, target.getLocation())) return;

        event.setCancelled(true);

        if (isEmptyLike(target)) {
            createFirstVerticalSlab(target, placeMat, side, player);
            player.swingMainHand();
        } else if ((target.getType() == Material.BARRIER || target.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(target)) {
            if (findDisplay(target, side) != null || !canAddSlabOnSide(target, side)) return;
            createSecondVerticalSlab(target, placeMat, side, player);
            player.swingMainHand();
        }
    }
    private String getOppositeSideStr(String side) {
        if (side == null) return "north";
        switch (side.toLowerCase()) {
            case "north": return "south";
            case "south": return "north";
            case "east": return "west";
            case "west": return "east";
            default: return "north";
        }
    }


    private void createFirstVerticalSlab(Block block, Material slabMat, String side, Player player) {
        if (!consumeOne(player)) return;


        setTrapdoorForSide(block, side);
        spawnVerticalDisplay(block, slabMat, side);

        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 1f);
    }

    private void createSecondVerticalSlab(Block block, Material slabMat, String side, Player player) {
        if (!consumeOne(player)) return;


        block.setType(Material.BARRIER, false);
        spawnVerticalDisplay(block, slabMat, side);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 1f);
    }

    private void setTrapdoorForSide(Block block, String side) {
        block.setType(Material.OAK_TRAPDOOR, false);
        BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.TrapDoor trapdoor) {

            trapdoor.setOpen(true);
            trapdoor.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
            trapdoor.setPowered(false);
            trapdoor.setWaterlogged(false);


            switch (side.toLowerCase()) {
                case "north":
                    trapdoor.setFacing(BlockFace.SOUTH);
                    break;
                case "south":
                    trapdoor.setFacing(BlockFace.NORTH);
                    break;
                case "east":
                    trapdoor.setFacing(BlockFace.WEST);
                    break;
                case "west":
                    trapdoor.setFacing(BlockFace.EAST);
                    break;
            }

            block.setBlockData(trapdoor, false);
        }
    }

    private void spawnVerticalDisplay(Block block, Material slabMat, String side) {
        Location loc = block.getLocation();
        World world = block.getWorld();

        world.spawn(loc, BlockDisplay.class, display -> {
            BlockData data = Bukkit.createBlockData(slabMat, bd -> {

                if (bd instanceof org.bukkit.block.data.type.Slab slab) {
                    slab.setType(org.bukkit.block.data.type.Slab.Type.BOTTOM);
                    slab.setWaterlogged(false);
                }
            });

            display.setBlock(data);
            display.setTransformation(createTransformationForSide(side));
            display.setPersistent(true);
            display.setGravity(false);
            display.setInterpolationDuration(0);
            display.setInterpolationDelay(0);

            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(keyVerticalSlab, PersistentDataType.BYTE, (byte) 1);
            pdc.set(keySide, PersistentDataType.STRING, side);
            pdc.set(keyMaterial, PersistentDataType.STRING, slabMat.name());
        });
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER && block.getType() != Material.OAK_TRAPDOOR) return;
        if (!isVerticalSlabAt(block)) return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;
        if (!ModuleAccess.canBuild(plugin, player, block.getLocation())) return;

        List<BlockDisplay> allDisplays = findAllDisplays(block);


        BlockDisplay target = getLookedDisplay(player, block);
        if (target == null) {
            if (allDisplays.isEmpty()) return;
            target = allDisplays.get(0);
        }


        Material slabMat = target.getBlock().getMaterial();
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        String side = getVerticalSideString(pdc);


        if (allDisplays.size() >= 2) {

            Material fullBlockMat = getFullBlockFromSlab(slabMat);
            block.setType(fullBlockMat, false);


            target.setVisibleByDefault(false);
        }


        activeMining.put(player.getUniqueId(), new MiningContext(block.getLocation(), side, slabMat, target, allDisplays.size()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        MiningContext ctx = activeMining.remove(event.getPlayer().getUniqueId());
        if (ctx != null) {
            restoreBarrier(ctx);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        MiningContext ctx = activeMining.remove(event.getPlayer().getUniqueId());
        if (ctx != null) {
            restoreBarrier(ctx);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        MiningContext ctx = activeMining.remove(player.getUniqueId());

        Block block = event.getBlock();
        if (!ModuleAccess.canBuild(plugin, player, block.getLocation())) return;


        if (ctx != null && ctx.location.equals(block.getLocation())) {
            event.setCancelled(true);
            event.setDropItems(false);

            processSlabBreak(block, player, ctx.side, ctx.slabMaterial, ctx.display);
            return;
        }


        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) return;


        if (block.getType() != Material.BARRIER && block.getType() != Material.OAK_TRAPDOOR) return;
        if (!isVerticalSlabAt(block)) return;

        event.setCancelled(true);
        event.setDropItems(false);

        BlockDisplay target = getLookedDisplay(player, block);
        if (target == null) {
            List<BlockDisplay> displays = findAllDisplays(block);
            if (displays.isEmpty()) {
                block.setType(Material.AIR, false);
                return;
            }
            target = displays.get(0);
        }

        Material mat = target.getBlock().getMaterial();
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        String side = getVerticalSideString(pdc);

        processSlabBreak(block, player, side, mat, target);
    }

    private void processSlabBreak(Block block, Player player, String side, Material slabMat, BlockDisplay target) {

        if (player.getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(slabMat, 1)
            );
        }


        target.remove();


        List<BlockDisplay> remaining = findAllDisplays(block);
        if (remaining.isEmpty()) {

            block.setType(Material.AIR, false);
        } else if (remaining.size() == 1) {

            BlockDisplay lastDisplay = remaining.get(0);
            PersistentDataContainer pdc = lastDisplay.getPersistentDataContainer();
            String lastSide = getVerticalSideString(pdc);
            if (lastSide != null) {
                setTrapdoorForSide(block, lastSide);
            }
        } else {

            block.setType(Material.BARRIER, false);
        }

        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 1f);
        block.getWorld().spawnParticle(
                Particle.BLOCK,
                block.getLocation().add(0.5, 0.5, 0.5),
                20,
                0.3, 0.3, 0.3,
                0.05,
                Bukkit.createBlockData(slabMat)
        );
    }

    private void restoreBarrier(MiningContext ctx) {
        Block block = ctx.location.getBlock();

        List<BlockDisplay> displays = findAllDisplays(block);
        if (ctx.displayCount >= 2) {
            block.setType(Material.BARRIER, false);
        } else if (displays.size() == 1) {
            setTrapdoorForSide(block, ctx.side);
        }


        if (ctx.displayCount >= 2 && ctx.display != null && !ctx.display.isDead()) {
            ctx.display.setVisibleByDefault(true);
        }
    }

    private Material getFullBlockFromSlab(Material slabMat) {

        String slabName = slabMat.name();
        if (slabName.endsWith("_SLAB")) {
            String blockName = slabName.substring(0, slabName.length() - 5);
            try {
                Material fullBlock = Material.valueOf(blockName);
                if (fullBlock != null && fullBlock.isBlock()) {
                    return fullBlock;
                }
            } catch (IllegalArgumentException e) {

                if (slabName.contains("_SLAB")) {
                    try {
                        Material planks = Material.valueOf(blockName + "_PLANKS");
                        if (planks != null && planks.isBlock()) {
                            return planks;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        return Material.STONE;
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if ((block.getType() == Material.BARRIER || block.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(block)) {
                it.remove();
                cleanupVerticalSlab(block, true);
                block.setType(Material.AIR, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if ((block.getType() == Material.BARRIER || block.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(block)) {
                it.remove();
                cleanupVerticalSlab(block, true);
                block.setType(Material.AIR, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if ((block.getType() == Material.BARRIER || block.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if ((block.getType() == Material.BARRIER || block.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Block against = event.getBlockAgainst();

        if ((block.getType() == Material.BARRIER || block.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(block)) {
            event.setCancelled(true);
            return;
        }

        if ((against.getType() == Material.BARRIER || against.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(against)) {
            if (block.getLocation().equals(against.getLocation())) {
                event.setCancelled(true);
            }
        }
    }


    private boolean consumeOne(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return true;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            return false;
        }

        hand.setAmount(hand.getAmount() - 1);
        return true;
    }

    private boolean isSlabItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getType().name().endsWith("_SLAB");
    }

    private boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private boolean isEmptyLike(Block block) {
        return block.getType().isAir();
    }

    private boolean canUseAsVerticalTarget(Block block) {
        return isEmptyLike(block) || ((block.getType() == Material.BARRIER || block.getType() == Material.OAK_TRAPDOOR) && isVerticalSlabAt(block));
    }

    private boolean canAddSlabOnSide(Block block, String newSide) {

        List<BlockDisplay> displays = findAllDisplays(block);
        if (displays.isEmpty()) return true;


        for (BlockDisplay display : displays) {
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            String existingSide = getVerticalSideString(pdc);
            if (existingSide == null) continue;


            boolean isOpposite = (existingSide.equals("north") && newSide.equals("south")) ||
                                (existingSide.equals("south") && newSide.equals("north")) ||
                                (existingSide.equals("east") && newSide.equals("west")) ||
                                (existingSide.equals("west") && newSide.equals("east"));

            if (!isOpposite) return false;
        }
        return true;
    }

    private boolean isVerticalSlabDisplay(BlockDisplay bd) {
        return hasVerticalSlabMarker(bd.getPersistentDataContainer());
    }

    private boolean isVerticalSlabAt(Block block) {
        return !findAllDisplays(block).isEmpty();
    }

    private int countDisplays(Block block) {
        return findAllDisplays(block).size();
    }

    private BlockDisplay findDisplay(Block block, String side) {
        for (BlockDisplay bd : findAllDisplays(block)) {
            String stored = getVerticalSideString(bd.getPersistentDataContainer());
            if (side.equalsIgnoreCase(stored)) {
                return bd;
            }
        }
        return null;
    }

    private List<BlockDisplay> findAllDisplays(Block block) {
        List<BlockDisplay> displays = new ArrayList<>();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (!(entity instanceof BlockDisplay bd)) continue;
            if (!isVerticalSlabDisplay(bd)) continue;

            Location loc = bd.getLocation();
            if (loc.getBlockX() == block.getX()
                    && loc.getBlockY() == block.getY()
                    && loc.getBlockZ() == block.getZ()) {
                displays.add(bd);
            }
        }

        return displays;
    }

    private void cleanupVerticalSlab(Block block, boolean dropItems) {
        for (BlockDisplay bd : findAllDisplays(block)) {
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

    private BlockDisplay getLookedDisplay(Player player, Block block) {
        String lookedSide = getTargetSide(player, block);
        BlockDisplay exact = findDisplay(block, lookedSide);
        if (exact != null) return exact;

        List<BlockDisplay> displays = findAllDisplays(block);
        if (displays.isEmpty()) return null;
        return displays.get(0);
    }

    private String getTargetSide(Player player, Block block) {
        var ray = player.rayTraceBlocks(5.0);
        if (ray != null && ray.getHitPosition() != null) {
            double hitX = ray.getHitPosition().getX() - block.getX();
            double hitZ = ray.getHitPosition().getZ() - block.getZ();

            double dx = hitX - 0.5;
            double dz = hitZ - 0.5;

            if (Math.abs(dx) > Math.abs(dz)) {
                return dx > 0 ? "east" : "west";
            } else {
                return dz > 0 ? "south" : "north";
            }
        }

        Vector dir = player.getLocation().getDirection().normalize();
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
            return dir.getX() > 0 ? "east" : "west";
        } else {
            return dir.getZ() > 0 ? "south" : "north";
        }
    }


    private Transformation createTransformationForSide(String side) {
        switch (side.toLowerCase()) {
            case "south":
                return southTransform;
            case "west":
                return westTransform;
            case "north":
                return northTransform;
            case "east":
                return eastTransform;
            default:
                return new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(1f, 1f, 1f),
                        new AxisAngle4f(0, 0, 0, 1)
                );
        }
    }


    private static class MiningContext {
        final Location location;
        final String side;
        final Material slabMaterial;
        final BlockDisplay display;
        final int displayCount;

        MiningContext(Location location, String side, Material slabMaterial, BlockDisplay display, int displayCount) {
            this.location = location;
            this.side = side;
            this.slabMaterial = slabMaterial;
            this.display = display;
            this.displayCount = displayCount;
        }
    }
}
