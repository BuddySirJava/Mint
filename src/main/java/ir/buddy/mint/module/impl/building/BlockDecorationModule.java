package ir.buddy.mint.module.impl.building;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class BlockDecorationModule implements Module, Listener {

    private final MintPlugin plugin;
    private boolean registered = false;

    private NamespacedKey decorationKey;
    private NamespacedKey decorationMaterialKey;
    private NamespacedKey decorationBlockLocKey;
    private NamespacedKey verticalSlabKey;
    private NamespacedKey verticalSideKey;
    private NamespacedKey mixedSlabKey;
    private NamespacedKey mixedSlabHalfKey;
    
    private NamespacedKey decorationPartKey;

    
    private static final String LEGACY_DECORATION_PLUGIN_NAMESPACE = "bukkuark";

    private NamespacedKey legacyDecorationKey;
    private NamespacedKey legacyDecorationMaterialKey;
    private NamespacedKey legacyDecorationBlockLocKey;
    private NamespacedKey legacyVerticalSlabKey;
    private NamespacedKey legacyVerticalSideKey;
    private NamespacedKey legacyMixedSlabKey;
    private NamespacedKey legacyMixedSlabHalfKey;
    private NamespacedKey legacyDecorationPartKey;

    private static final String PART_VERTICAL = "vertical:";
    private static final String PART_MIXED = "mixed:";

    private static final float PX = 1f / 16f;
    private static final float HALF_PAD = 0.001f;
    private static final float CUSTOM_SLAB_INSET = 0.0008f;
    private static final AxisAngle4f NO_ROTATION = new AxisAngle4f(0, 0, 0, 1);
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public BlockDecorationModule(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Block Decoration";
    }

    @Override
    public String getConfigPath() {
        return "modules.blockdecoration";
    }

    @Override
    public String getDescription() {
        return "Decorate supported blocks with display skins.";
    }

    @Override
    public void enable() {
        if (!registered) {
            decorationKey = new NamespacedKey(plugin, "decoration");
            decorationMaterialKey = new NamespacedKey(plugin, "decoration_material");
            decorationBlockLocKey = new NamespacedKey(plugin, "decoration_block_loc");
            verticalSlabKey = new NamespacedKey(plugin, "vertical_slab");
            verticalSideKey = new NamespacedKey(plugin, "vertical_side");
            mixedSlabKey = new NamespacedKey(plugin, "mixed_slab");
            mixedSlabHalfKey = new NamespacedKey(plugin, "slab_half");
            decorationPartKey = new NamespacedKey(plugin, "decoration_part");
            legacyDecorationKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "decoration");
            legacyDecorationMaterialKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "decoration_material");
            legacyDecorationBlockLocKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "decoration_block_loc");
            legacyVerticalSlabKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "vertical_slab");
            legacyVerticalSideKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "vertical_side");
            legacyMixedSlabKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "mixed_slab");
            legacyMixedSlabHalfKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "slab_half");
            legacyDecorationPartKey = new NamespacedKey(LEGACY_DECORATION_PLUGIN_NAMESPACE, "decoration_part");
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        registered = false;
    }

    private boolean hasDecorationMarker(PersistentDataContainer pdc) {
        return pdc.has(decorationKey, PersistentDataType.BYTE)
                || pdc.has(legacyDecorationKey, PersistentDataType.BYTE);
    }

    private @Nullable String getDecorationBlockLocString(PersistentDataContainer pdc) {
        if (pdc.has(decorationBlockLocKey, PersistentDataType.STRING)) {
            return pdc.get(decorationBlockLocKey, PersistentDataType.STRING);
        }
        if (pdc.has(legacyDecorationBlockLocKey, PersistentDataType.STRING)) {
            return pdc.get(legacyDecorationBlockLocKey, PersistentDataType.STRING);
        }
        return null;
    }

    private @Nullable String getDecorationMaterialString(PersistentDataContainer pdc) {
        if (pdc.has(decorationMaterialKey, PersistentDataType.STRING)) {
            return pdc.get(decorationMaterialKey, PersistentDataType.STRING);
        }
        if (pdc.has(legacyDecorationMaterialKey, PersistentDataType.STRING)) {
            return pdc.get(legacyDecorationMaterialKey, PersistentDataType.STRING);
        }
        return null;
    }

    private @Nullable String getDecorationPartString(PersistentDataContainer pdc) {
        if (pdc.has(decorationPartKey, PersistentDataType.STRING)) {
            return pdc.get(decorationPartKey, PersistentDataType.STRING);
        }
        if (pdc.has(legacyDecorationPartKey, PersistentDataType.STRING)) {
            return pdc.get(legacyDecorationPartKey, PersistentDataType.STRING);
        }
        return null;
    }

    private boolean hasVerticalSlabMarker(PersistentDataContainer pdc) {
        return pdc.has(verticalSlabKey, PersistentDataType.BYTE)
                || pdc.has(legacyVerticalSlabKey, PersistentDataType.BYTE);
    }

    private @Nullable String getVerticalSideString(PersistentDataContainer pdc) {
        if (pdc.has(verticalSideKey, PersistentDataType.STRING)) {
            return pdc.get(verticalSideKey, PersistentDataType.STRING);
        }
        if (pdc.has(legacyVerticalSideKey, PersistentDataType.STRING)) {
            return pdc.get(legacyVerticalSideKey, PersistentDataType.STRING);
        }
        return null;
    }

    private boolean hasMixedSlabMarker(PersistentDataContainer pdc) {
        return pdc.has(mixedSlabKey, PersistentDataType.BYTE)
                || pdc.has(legacyMixedSlabKey, PersistentDataType.BYTE);
    }

    private @Nullable String getMixedSlabHalfString(PersistentDataContainer pdc) {
        if (pdc.has(mixedSlabHalfKey, PersistentDataType.STRING)) {
            return pdc.get(mixedSlabHalfKey, PersistentDataType.STRING);
        }
        if (pdc.has(legacyMixedSlabHalfKey, PersistentDataType.STRING)) {
            return pdc.get(legacyMixedSlabHalfKey, PersistentDataType.STRING);
        }
        return null;
    }

    private boolean isVerticalSlab(Block block) {
        if (block.getType() != Material.OAK_TRAPDOOR && block.getType() != Material.BARRIER) {
            return false;
        }

        Location loc = block.getLocation();
        BoundingBox box = new BoundingBox(
                loc.getX() - 0.5, loc.getY() - 0.5, loc.getZ() - 0.5,
                loc.getX() + 1.5, loc.getY() + 1.5, loc.getZ() + 1.5);

        for (Entity entity : block.getWorld().getNearbyEntities(box, e -> e instanceof BlockDisplay)) {
            if (hasVerticalSlabMarker(entity.getPersistentDataContainer())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPressurePlate(Material m) {
        return Tag.PRESSURE_PLATES.isTagged(m);
    }

    private static boolean isCarpet(Material m) {
        return Tag.WOOL_CARPETS.isTagged(m) || m == Material.MOSS_CARPET;
    }

    private static boolean isCauldron(Material m) {
        return Tag.CAULDRONS.isTagged(m);
    }

    private static boolean isButton(Material m) {
        return Tag.BUTTONS.isTagged(m);
    }

    private static boolean isPot(Material m) {
        String name = m.name();
        return name.equals("FLOWER_POT") || name.startsWith("POTTED_") || name.equals("DECORATED_POT");
    }

    private static boolean isFenceLike(BlockData data) {
        return data instanceof Fence || data instanceof Gate;
    }

    private static boolean isDecoratable(BlockData data, Material type) {
        return data instanceof Stairs
                || data instanceof Slab
                || data instanceof Fence
                || data instanceof Gate
                || data instanceof Door
                || data instanceof TrapDoor
                || data instanceof Ladder
                || isPressurePlate(type)
                || isCarpet(type)
                || isCauldron(type)
                || isButton(type)
                || isPot(type);
    }

    private boolean isMixedSlab(Block block) {
        if (MixedSlabModule.isMixedSlabBlock(plugin, block)) {
            return true;
        }

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (!(entity instanceof BlockDisplay display))
                continue;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!hasMixedSlabMarker(pdc))
                continue;
            Location eLoc = display.getLocation();
            if (eLoc.getBlockX() == block.getX() && eLoc.getBlockY() == block.getY() && eLoc.getBlockZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    private List<String> getVerticalSlabSides(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        List<String> sides = new ArrayList<>(2);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (!(entity instanceof BlockDisplay display))
                continue;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!hasVerticalSlabMarker(pdc))
                continue;
            Location eLoc = display.getLocation();
            if (eLoc.getBlockX() != block.getX() || eLoc.getBlockY() != block.getY() || eLoc.getBlockZ() != block.getZ())
                continue;
            String side = getVerticalSideString(pdc);
            if (side != null) {
                sides.add(side.toLowerCase());
            }
        }
        return sides;
    }

    private Set<String> getMixedSlabHalves(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Set<String> halves = new java.util.HashSet<>(2);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (!(entity instanceof BlockDisplay display))
                continue;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!hasMixedSlabMarker(pdc))
                continue;
            Location eLoc = display.getLocation();
            if (eLoc.getBlockX() != block.getX() || eLoc.getBlockY() != block.getY() || eLoc.getBlockZ() != block.getZ())
                continue;
            String half = getMixedSlabHalfString(pdc);
            if (half != null) {
                halves.add(half.toLowerCase());
            }
        }
        return halves;
    }

    private static String rayHitVerticalSide(Player player, Block block) {
        var ray = player.rayTraceBlocks(5.0);
        if (ray != null && ray.getHitPosition() != null) {
            double hitX = ray.getHitPosition().getX() - block.getX();
            double hitZ = ray.getHitPosition().getZ() - block.getZ();
            double dx = hitX - 0.5;
            double dz = hitZ - 0.5;
            if (Math.abs(dx) > Math.abs(dz)) {
                return dx > 0 ? "east" : "west";
            }
            return dz > 0 ? "south" : "north";
        }
        Vector dir = player.getLocation().getDirection().normalize();
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
            return dir.getX() > 0 ? "east" : "west";
        }
        return dir.getZ() > 0 ? "south" : "north";
    }

    private static String rayHitMixedHalf(Player player, Block block) {
        var rayResult = player.rayTraceBlocks(5.0);
        if (rayResult != null && rayResult.getHitPosition() != null) {
            double relativeY = rayResult.getHitPosition().getY() - block.getY();
            return relativeY > 0.5 ? "top" : "bottom";
        }
        return player.getEyeLocation().getY() > block.getY() + 0.5 ? "top" : "bottom";
    }

    private String resolveVerticalDecorationSide(Player player, Block block) {
        List<String> sides = getVerticalSlabSides(block);
        if (sides.isEmpty()) {
            return "north";
        }
        String ray = rayHitVerticalSide(player, block);
        if (sides.contains(ray)) {
            return ray;
        }
        return sides.get(0);
    }

    private String resolveMixedDecorationHalf(Player player, Block block) {
        return rayHitMixedHalf(player, block);
    }

    private void dropDecorationItem(Block block, BlockDisplay display) {
        String matName = getDecorationMaterialString(display.getPersistentDataContainer());
        if (matName != null) {
            Material mat = Material.getMaterial(matName);
            if (mat != null) {
                block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(mat, 1));
            }
        }
    }

    
    private void dropDecorationItemsForRemovedDisplays(Block block, List<BlockDisplay> displays) {
        Set<String> compositeSkinMaterialDropped = new HashSet<>();
        for (BlockDisplay d : displays) {
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            String matName = getDecorationMaterialString(pdc);
            if (matName == null) {
                continue;
            }
            Material mat = Material.getMaterial(matName);
            if (mat == null) {
                continue;
            }
            String partKey = getDecorationPartString(pdc);
            if (partKey == null && !compositeSkinMaterialDropped.add(matName)) {
                continue;
            }
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(mat, 1));
        }
    }

    private void removeDecorationPartsForApply(Block block, String newPartKey) {
        for (BlockDisplay d : new ArrayList<>(findDecorationsAt(block))) {
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            String existing = getDecorationPartString(pdc);
            if (Objects.equals(existing, newPartKey)) {
                dropDecorationItem(block, d);
                d.remove();
            } else if (existing == null && newPartKey != null) {
                if (newPartKey.startsWith(PART_VERTICAL) && isVerticalSlab(block)) {
                    dropDecorationItem(block, d);
                    d.remove();
                } else if (newPartKey.startsWith(PART_MIXED) && isMixedSlab(block)) {
                    dropDecorationItem(block, d);
                    d.remove();
                }
            }
        }
    }

    private void spawnVerticalSlabSkinAtSide(Block block, Material material, String locKey, String sideLower) {
        String part = PART_VERTICAL + sideLower;
        switch (sideLower) {
            case "north" -> spawnCustomSlabPart(block, material, locKey, 0, 0, 0, 1.0f, 1.0f, 0.5f, part);
            case "south" -> spawnCustomSlabPart(block, material, locKey, 0, 0, 0.5f, 1.0f, 1.0f, 0.5f, part);
            case "west" -> spawnCustomSlabPart(block, material, locKey, 0, 0, 0, 0.5f, 1.0f, 1.0f, part);
            case "east" -> spawnCustomSlabPart(block, material, locKey, 0.5f, 0, 0, 0.5f, 1.0f, 1.0f, part);
            default -> {
            }
        }
    }

    private void buildVerticalSlabAllSidesNoRemove(Block block, Material material) {
        List<String> sides = getVerticalSlabSides(block);
        if (sides.isEmpty()) {
            return;
        }
        String locKey = encodeBlockLocation(block);
        for (String side : sides) {
            spawnVerticalSlabSkinAtSide(block, material, locKey, side);
        }
    }

    private void buildVerticalSlabOneSide(Block block, Material material, String sideLower) {
        String locKey = encodeBlockLocation(block);
        spawnVerticalSlabSkinAtSide(block, material, locKey, sideLower.toLowerCase());
    }

    private void buildMixedSlabAllHalvesNoRemove(Block block, Material material) {
        Set<String> halves = getMixedSlabHalves(block);
        if (halves.isEmpty()) {
            return;
        }
        String locKey = encodeBlockLocation(block);
        if (halves.contains("bottom")) {
            spawnCustomSlabPart(block, material, locKey, 0, 0, 0, 1.0f, 0.5f, 1.0f, PART_MIXED + "bottom");
        }
        if (halves.contains("top")) {
            spawnCustomSlabPart(block, material, locKey, 0, 0.5f, 0, 1.0f, 0.5f, 1.0f, PART_MIXED + "top");
        }
    }

    private void buildMixedSlabOneHalf(Block block, Material material, String halfLower) {
        String locKey = encodeBlockLocation(block);
        String h = halfLower.toLowerCase();
        if ("bottom".equals(h)) {
            spawnCustomSlabPart(block, material, locKey, 0, 0, 0, 1.0f, 0.5f, 1.0f, PART_MIXED + "bottom");
        } else if ("top".equals(h)) {
            spawnCustomSlabPart(block, material, locKey, 0, 0.5f, 0, 1.0f, 0.5f, 1.0f, PART_MIXED + "top");
        }
    }

    private void applyDecorationPart(Block block, Material material, String decorationPart) {
        if (decorationPart.startsWith(PART_VERTICAL)) {
            buildVerticalSlabOneSide(block, material, decorationPart.substring(PART_VERTICAL.length()));
        } else if (decorationPart.startsWith(PART_MIXED)) {
            buildMixedSlabOneHalf(block, material, decorationPart.substring(PART_MIXED.length()));
        }
    }

    private record DecorationSkin(Material material, @Nullable String partKey) {
    }

    private static boolean isDisallowedSkinMaterial(Material m) {
        return Tag.DOORS.isTagged(m)
                || Tag.FENCES.isTagged(m)
                || Tag.FENCE_GATES.isTagged(m)
                || Tag.TRAPDOORS.isTagged(m)
                || Tag.CAULDRONS.isTagged(m)
                || Tag.BUTTONS.isTagged(m)
                || m == Material.LADDER
                || isPot(m);
    }

    private static boolean isSolidBlock(Material m) {
        return m.isBlock() && m.isSolid() && !m.isAir();
    }

    private static boolean isVanillaDecorationItem(ItemStack itemStack) {
        Material material = itemStack.getType();
        if (!isSolidBlock(material) || isDisallowedSkinMaterial(material)) {
            return false;
        }

        if (!itemStack.hasItemMeta()) {
            return true;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return true;
        }


        return !meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasEnchants()
                && !meta.hasAttributeModifiers()
                && !meta.isUnbreakable()
                && !meta.hasCustomModelData()
                && meta.getItemFlags().isEmpty()
                && meta.getPersistentDataContainer().isEmpty();
    }

    private String encodeBlockLocation(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private List<BlockDisplay> findDecorationsAt(Block block) {
        String locKey = encodeBlockLocation(block);
        int bx = block.getX(), by = block.getY(), bz = block.getZ();
        BoundingBox box = new BoundingBox(bx - 0.5, by - 0.5, bz - 0.5, bx + 1.5, by + 1.5, bz + 1.5);

        List<BlockDisplay> results = new ArrayList<>(6);
        for (Entity entity : block.getWorld().getNearbyEntities(box, e -> e instanceof BlockDisplay)) {
            BlockDisplay display = (BlockDisplay) entity;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (hasDecorationMarker(pdc) && locKey.equals(getDecorationBlockLocString(pdc))) {
                results.add(display);
            }
        }
        return results;
    }

    private void removeDecorations(List<BlockDisplay> displays) {
        for (int i = 0, n = displays.size(); i < n; i++) {
            displays.get(i).remove();
        }
    }

    private void removeDecorationsAt(Block block) {
        removeDecorations(findDecorationsAt(block));
    }

    private String getDecorationMaterialName(List<BlockDisplay> displays) {
        if (displays.isEmpty())
            return null;
        return getDecorationMaterialString(displays.get(0).getPersistentDataContainer());
    }

    private Material getDecorationMaterial(Block block) {
        String name = getDecorationMaterialName(findDecorationsAt(block));
        return name != null ? Material.getMaterial(name) : null;
    }

    private boolean isValidDecoratedTarget(Block block) {
        Material blockType = block.getType();
        BlockData data = block.getBlockData();

        if (data instanceof Door door) {
            Block otherHalf = door.getHalf() == Bisected.Half.TOP
                    ? block.getRelative(BlockFace.DOWN)
                    : block.getRelative(BlockFace.UP);
            return otherHalf.getBlockData() instanceof Door;
        }

        return isDecoratable(data, blockType) || isVerticalSlab(block) || isMixedSlab(block);
    }

    private static BlockFace rotateClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private static BlockFace rotateCounterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private void cleanupOrphanedDecoration(Block block) {
        List<BlockDisplay> displays = findDecorationsAt(block);
        if (displays.isEmpty())
            return;

        if (isValidDecoratedTarget(block))
            return;

        Set<String> matNames = new HashSet<>();
        for (BlockDisplay d : displays) {
            String matName = getDecorationMaterialString(d.getPersistentDataContainer());
            if (matName != null) {
                matNames.add(matName);
            }
        }
        removeDecorations(displays);
        for (String matName : matNames) {
            removeMatchingOrphanedDoorHalf(block.getRelative(BlockFace.UP), matName);
            removeMatchingOrphanedDoorHalf(block.getRelative(BlockFace.DOWN), matName);
            Material mat = Material.getMaterial(matName);
            if (mat != null) {
                block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(mat, 1));
            }
        }
    }

    private void removeMatchingOrphanedDoorHalf(Block block, String matName) {
        if (matName == null || isValidDecoratedTarget(block))
            return;

        List<BlockDisplay> displays = findDecorationsAt(block);
        if (displays.isEmpty())
            return;

        if (matName.equals(getDecorationMaterialName(displays))) {
            removeDecorations(displays);
        }
    }


    private void spawnPart(Block block, Material material, String locKey,
            float tx, float ty, float tz,
            float sx, float sy, float sz) {
        spawnPart(block, material, locKey, tx, ty, tz, sx, sy, sz, HALF_PAD, null);
    }

    private void spawnPart(Block block, Material material, String locKey,
            float tx, float ty, float tz,
            float sx, float sy, float sz,
            float pad) {
        spawnPart(block, material, locKey, tx, ty, tz, sx, sy, sz, pad, null);
    }

    private void spawnPart(Block block, Material material, String locKey,
            float tx, float ty, float tz,
            float sx, float sy, float sz,
            float pad,
            @Nullable String decorationPart) {
        Location spawnLoc = block.getLocation();
        if (!plugin.getDisplayEntityController().canSpawn(spawnLoc, getConfigPath())) {
            return;
        }
        Display.Brightness brightness = sampleBrightness(block);
        block.getWorld().spawn(spawnLoc, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(
                    new Vector3f(tx - pad, ty - pad, tz - pad),
                    NO_ROTATION,
                    new Vector3f(sx + pad * 2, sy + pad * 2, sz + pad * 2),
                    NO_ROTATION));
            display.setBrightness(brightness);
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(decorationKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(decorationMaterialKey, PersistentDataType.STRING, material.name());
            pdc.set(decorationBlockLocKey, PersistentDataType.STRING, locKey);
            if (decorationPart != null) {
                pdc.set(decorationPartKey, PersistentDataType.STRING, decorationPart);
            }
            plugin.getDisplayEntityController().markManaged(display, getConfigPath());
            display.setPersistent(true);
            display.setGravity(false);
        });
    }

    private Display.Brightness sampleBrightness(Block block) {
        int totalSky = 0;
        int totalBlock = 0;
        int samples = 0;

        Block[] positions = new Block[] {
                block,
                block.getRelative(BlockFace.UP),
                block.getRelative(BlockFace.DOWN),
                block.getRelative(BlockFace.NORTH),
                block.getRelative(BlockFace.SOUTH),
                block.getRelative(BlockFace.EAST),
                block.getRelative(BlockFace.WEST)
        };

        for (Block sampled : positions) {
            totalSky += sampled.getLightFromSky();
            totalBlock += sampled.getLightFromBlocks();
            samples++;
        }

        int avgSky = Math.min(15, totalSky / samples);
        int avgBlock = Math.min(15, totalBlock / samples);
        return new Display.Brightness(avgBlock, avgSky);
    }

    private void spawnPart(Block block, Material material,
            float tx, float ty, float tz,
            float sx, float sy, float sz) {
        spawnPart(block, material, encodeBlockLocation(block), tx, ty, tz, sx, sy, sz, HALF_PAD, null);
    }

    private void spawnCustomSlabPart(Block block, Material material, String locKey,
            float tx, float ty, float tz,
            float sx, float sy, float sz,
            @Nullable String decorationPart) {
        spawnPart(block, material, locKey, tx, ty, tz, sx, sy, sz, -CUSTOM_SLAB_INSET, decorationPart);
    }


    private void buildDoorDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Door door))
            return;

        Block bottomBlock;
        Block topBlock;

        if (door.getHalf() == Bisected.Half.TOP) {
            topBlock = block;
            bottomBlock = block.getRelative(BlockFace.DOWN);
            data = bottomBlock.getBlockData();
            if (!(data instanceof Door))
                return;
            door = (Door) data;
        } else {
            bottomBlock = block;
            topBlock = block.getRelative(BlockFace.UP);
        }

        Door.Hinge hinge = Door.Hinge.LEFT;
        if (topBlock.getBlockData() instanceof Door topDoor) {
            hinge = topDoor.getHinge();
        }

        removeDecorationsAt(bottomBlock);
        removeDecorationsAt(topBlock);

        BlockFace facing = door.getFacing();
        boolean open = door.isOpen();
        float thickness = 3 * PX;

        BlockFace panelFace;
        if (!open) {
            panelFace = facing.getOppositeFace();
        } else {
            panelFace = hinge == Door.Hinge.LEFT
                    ? rotateCounterClockwise(facing)
                    : rotateClockwise(facing);
        }

        float tx, tz, sx, sz;
        switch (panelFace) {
            case SOUTH -> {
                tx = 0;
                tz = 1f - thickness;
                sx = 1.0f;
                sz = thickness;
            }
            case NORTH -> {
                tx = 0;
                tz = 0;
                sx = 1.0f;
                sz = thickness;
            }
            case EAST -> {
                tx = 1f - thickness;
                tz = 0;
                sx = thickness;
                sz = 1.0f;
            }
            case WEST -> {
                tx = 0;
                tz = 0;
                sx = thickness;
                sz = 1.0f;
            }
            default -> {
                tx = 0;
                tz = 0;
                sx = 1.0f;
                sz = thickness;
            }
        }

        String bottomLoc = encodeBlockLocation(bottomBlock);
        String topLoc = encodeBlockLocation(topBlock);
        spawnPart(bottomBlock, material, bottomLoc, tx, 0, tz, sx, 1.0f, sz);
        spawnPart(topBlock, material, topLoc, tx, 0, tz, sx, 1.0f, sz);
    }

    private void buildFenceDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Fence fence))
            return;

        removeDecorationsAt(block);

        String locKey = encodeBlockLocation(block);
        float postSize = 4 * PX;
        float postOffset = (1f - postSize) / 2f;
        spawnPart(block, material, locKey, postOffset, 0, postOffset, postSize, 1.0f, postSize);

        Set<BlockFace> connectedFaces = fence.getFaces();
        if (connectedFaces.isEmpty())
            return;

        float barWidth = 2 * PX;
        float barHeight = 3 * PX;
        float barLength = (1f - postSize) / 2f;
        float barCenterOffset = (1f - barWidth) / 2f;
        float lowerY = 6 * PX;
        float upperY = 12 * PX;

        for (BlockFace face : connectedFaces) {
            float bTx, bTz, bSx, bSz;
            switch (face) {
                case NORTH -> {
                    bTx = barCenterOffset;
                    bTz = 0;
                    bSx = barWidth;
                    bSz = barLength;
                }
                case SOUTH -> {
                    bTx = barCenterOffset;
                    bTz = postOffset + postSize;
                    bSx = barWidth;
                    bSz = barLength;
                }
                case WEST -> {
                    bTx = 0;
                    bTz = barCenterOffset;
                    bSx = barLength;
                    bSz = barWidth;
                }
                case EAST -> {
                    bTx = postOffset + postSize;
                    bTz = barCenterOffset;
                    bSx = barLength;
                    bSz = barWidth;
                }
                default -> {
                    continue;
                }
            }
            spawnPart(block, material, locKey, bTx, lowerY, bTz, bSx, barHeight, bSz);
            spawnPart(block, material, locKey, bTx, upperY, bTz, bSx, barHeight, bSz);
        }
    }

    private void buildGateDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Gate gate))
            return;

        removeDecorationsAt(block);

        String locKey = encodeBlockLocation(block);
        BlockFace facing = gate.getFacing();
        boolean open = gate.isOpen();
        boolean inWall = gate.isInWall();

        float postY = inWall ? 2 * PX : 5 * PX;
        float postH = 11 * PX;
        float railY1 = inWall ? 3 * PX : 6 * PX;
        float railY2 = inWall ? 9 * PX : 12 * PX;
        float railH = 3 * PX;
        float stileY = inWall ? 3 * PX : 6 * PX;
        float stileH = 9 * PX;

        if (!open) {
            if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
                spawnPart(block, material, locKey, 0 * PX, postY, 7 * PX, 2 * PX, postH, 2 * PX);
                spawnPart(block, material, locKey, 14 * PX, postY, 7 * PX, 2 * PX, postH, 2 * PX);
                spawnPart(block, material, locKey, 2 * PX, railY1, 7 * PX, 6 * PX, railH, 2 * PX);
                spawnPart(block, material, locKey, 2 * PX, railY2, 7 * PX, 6 * PX, railH, 2 * PX);
                spawnPart(block, material, locKey, 6 * PX, stileY, 7 * PX, 2 * PX, stileH, 2 * PX);
                spawnPart(block, material, locKey, 8 * PX, railY1, 7 * PX, 6 * PX, railH, 2 * PX);
                spawnPart(block, material, locKey, 8 * PX, railY2, 7 * PX, 6 * PX, railH, 2 * PX);
                spawnPart(block, material, locKey, 8 * PX, stileY, 7 * PX, 2 * PX, stileH, 2 * PX);
            } else {
                spawnPart(block, material, locKey, 7 * PX, postY, 0 * PX, 2 * PX, postH, 2 * PX);
                spawnPart(block, material, locKey, 7 * PX, postY, 14 * PX, 2 * PX, postH, 2 * PX);
                spawnPart(block, material, locKey, 7 * PX, railY1, 2 * PX, 2 * PX, railH, 6 * PX);
                spawnPart(block, material, locKey, 7 * PX, railY2, 2 * PX, 2 * PX, railH, 6 * PX);
                spawnPart(block, material, locKey, 7 * PX, stileY, 6 * PX, 2 * PX, stileH, 2 * PX);
                spawnPart(block, material, locKey, 7 * PX, railY1, 8 * PX, 2 * PX, railH, 6 * PX);
                spawnPart(block, material, locKey, 7 * PX, railY2, 8 * PX, 2 * PX, railH, 6 * PX);
                spawnPart(block, material, locKey, 7 * PX, stileY, 8 * PX, 2 * PX, stileH, 2 * PX);
            }
            return;
        }

        if (facing == BlockFace.SOUTH) {
            spawnPart(block, material, locKey, 0 * PX, postY, 7 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 14 * PX, postY, 7 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 0 * PX, railY1, 9 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 0 * PX, railY2, 9 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 0 * PX, stileY, 13 * PX, 2 * PX, stileH, 2 * PX);
            spawnPart(block, material, locKey, 14 * PX, railY1, 9 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 14 * PX, railY2, 9 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 14 * PX, stileY, 13 * PX, 2 * PX, stileH, 2 * PX);
        } else if (facing == BlockFace.NORTH) {
            spawnPart(block, material, locKey, 0 * PX, postY, 7 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 14 * PX, postY, 7 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 0 * PX, railY1, 1 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 0 * PX, railY2, 1 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 0 * PX, stileY, 1 * PX, 2 * PX, stileH, 2 * PX);
            spawnPart(block, material, locKey, 14 * PX, railY1, 1 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 14 * PX, railY2, 1 * PX, 2 * PX, railH, 6 * PX);
            spawnPart(block, material, locKey, 14 * PX, stileY, 1 * PX, 2 * PX, stileH, 2 * PX);
        } else if (facing == BlockFace.EAST) {
            spawnPart(block, material, locKey, 7 * PX, postY, 0 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 7 * PX, postY, 14 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 9 * PX, railY1, 0 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 9 * PX, railY2, 0 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 13 * PX, stileY, 0 * PX, 2 * PX, stileH, 2 * PX);
            spawnPart(block, material, locKey, 9 * PX, railY1, 14 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 9 * PX, railY2, 14 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 13 * PX, stileY, 14 * PX, 2 * PX, stileH, 2 * PX);
        } else if (facing == BlockFace.WEST) {
            spawnPart(block, material, locKey, 7 * PX, postY, 0 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 7 * PX, postY, 14 * PX, 2 * PX, postH, 2 * PX);
            spawnPart(block, material, locKey, 1 * PX, railY1, 0 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 1 * PX, railY2, 0 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 1 * PX, stileY, 0 * PX, 2 * PX, stileH, 2 * PX);
            spawnPart(block, material, locKey, 1 * PX, railY1, 14 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 1 * PX, railY2, 14 * PX, 6 * PX, railH, 2 * PX);
            spawnPart(block, material, locKey, 1 * PX, stileY, 14 * PX, 2 * PX, stileH, 2 * PX);
        }
    }

    private void buildStairsDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Stairs stairs))
            return;

        removeDecorationsAt(block);

        String locKey = encodeBlockLocation(block);
        BlockFace facing = stairs.getFacing();
        boolean upsideDown = stairs.getHalf() == Bisected.Half.TOP;
        Stairs.Shape shape = stairs.getShape();

        float slabH = 8 * PX;
        float slabY = upsideDown ? 0.5f : 0f;
        float stepY = upsideDown ? 0f : 0.5f;

        spawnPart(block, material, locKey, 0, slabY, 0, 1.0f, slabH, 1.0f);

        switch (shape) {
            case STRAIGHT -> {
                float stx = 0, stz = 0, ssx = 1.0f, ssz = 1.0f;
                switch (facing) {
                    case SOUTH -> {
                        stz = 0.5f;
                        ssz = 0.5f;
                    }
                    case NORTH -> ssz = 0.5f;
                    case WEST -> ssx = 0.5f;
                    case EAST -> {
                        stx = 0.5f;
                        ssx = 0.5f;
                    }
                }
                spawnPart(block, material, locKey, stx, stepY, stz, ssx, slabH, ssz);
            }
            case INNER_LEFT -> {
                switch (facing) {
                    case SOUTH -> {
                        spawnPart(block, material, locKey, 0, stepY, 0.5f, 1.0f, slabH, 0.5f);
                        spawnPart(block, material, locKey, 0.5f, stepY, 0, 0.5f, slabH, 0.5f);
                    }
                    case NORTH -> {
                        spawnPart(block, material, locKey, 0, stepY, 0, 1.0f, slabH, 0.5f);
                        spawnPart(block, material, locKey, 0, stepY, 0.5f, 0.5f, slabH, 0.5f);
                    }
                    case WEST -> {
                        spawnPart(block, material, locKey, 0, stepY, 0, 0.5f, slabH, 1.0f);
                        spawnPart(block, material, locKey, 0.5f, stepY, 0.5f, 0.5f, slabH, 0.5f);
                    }
                    case EAST -> {
                        spawnPart(block, material, locKey, 0.5f, stepY, 0, 0.5f, slabH, 1.0f);
                        spawnPart(block, material, locKey, 0, stepY, 0, 0.5f, slabH, 0.5f);
                    }
                }
            }
            case INNER_RIGHT -> {
                switch (facing) {
                    case SOUTH -> {
                        spawnPart(block, material, locKey, 0, stepY, 0.5f, 1.0f, slabH, 0.5f);
                        spawnPart(block, material, locKey, 0, stepY, 0, 0.5f, slabH, 0.5f);
                    }
                    case NORTH -> {
                        spawnPart(block, material, locKey, 0, stepY, 0, 1.0f, slabH, 0.5f);
                        spawnPart(block, material, locKey, 0.5f, stepY, 0.5f, 0.5f, slabH, 0.5f);
                    }
                    case WEST -> {
                        spawnPart(block, material, locKey, 0, stepY, 0, 0.5f, slabH, 1.0f);
                        spawnPart(block, material, locKey, 0.5f, stepY, 0, 0.5f, slabH, 0.5f);
                    }
                    case EAST -> {
                        spawnPart(block, material, locKey, 0.5f, stepY, 0, 0.5f, slabH, 1.0f);
                        spawnPart(block, material, locKey, 0, stepY, 0.5f, 0.5f, slabH, 0.5f);
                    }
                }
            }
            case OUTER_LEFT -> {
                float stx = 0, stz = 0;
                switch (facing) {
                    case SOUTH -> {
                        stx = 0.5f;
                        stz = 0.5f;
                    }
                    case NORTH -> {
                    }
                    case WEST -> stz = 0.5f;
                    case EAST -> stx = 0.5f;
                }
                spawnPart(block, material, locKey, stx, stepY, stz, 0.5f, slabH, 0.5f);
            }
            case OUTER_RIGHT -> {
                float stx = 0, stz = 0;
                switch (facing) {
                    case SOUTH -> stz = 0.5f;
                    case NORTH -> stx = 0.5f;
                    case WEST -> {
                    }
                    case EAST -> {
                        stx = 0.5f;
                        stz = 0.5f;
                    }
                }
                spawnPart(block, material, locKey, stx, stepY, stz, 0.5f, slabH, 0.5f);
            }
        }
    }

    private void buildSlabDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Slab slab))
            return;

        removeDecorationsAt(block);
        String locKey = encodeBlockLocation(block);

        float ty = 0;
        float sy = 0.5f;

        switch (slab.getType()) {
            case BOTTOM -> {
                ty = 0;
                sy = 0.5f;
            }
            case TOP -> {
                ty = 0.5f;
                sy = 0.5f;
            }
            case DOUBLE -> {
                ty = 0;
                sy = 1.0f;
            }
        }

        spawnPart(block, material, locKey, 0, ty, 0, 1.0f, sy, 1.0f);
    }

    private void buildTrapdoorDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof TrapDoor trapdoor))
            return;

        removeDecorationsAt(block);

        float thickness = 3 * PX;

        if (trapdoor.isOpen()) {
            float tx = 0, tz = 0, sx = 1.0f, sz = 1.0f;
            switch (trapdoor.getFacing().getOppositeFace()) {
                case NORTH -> sz = thickness;
                case SOUTH -> {
                    tz = 1f - thickness;
                    sz = thickness;
                }
                case WEST -> sx = thickness;
                case EAST -> {
                    tx = 1f - thickness;
                    sx = thickness;
                }
            }
            spawnPart(block, material, tx, 0, tz, sx, 1.0f, sz);
        } else {
            float ty = trapdoor.getHalf() == Bisected.Half.TOP ? (1f - thickness) : 0;
            spawnPart(block, material, 0, ty, 0, 1.0f, thickness, 1.0f);
        }
    }

    private void buildLadderDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Ladder ladder))
            return;

        removeDecorationsAt(block);
        String locKey = encodeBlockLocation(block);

        float depth = 3 * PX;
        float railW = 2 * PX;
        float rungH = 2 * PX;
        float rungW = 14 * PX;


        float[] rungYs = { 1 * PX, 5 * PX, 9 * PX, 13 * PX };

        switch (ladder.getFacing()) {
            case NORTH -> {
                float tz = 1f - depth;

                spawnPart(block, material, locKey, 2 * PX, 0, tz, railW, 1.0f, depth);
                spawnPart(block, material, locKey, 12 * PX, 0, tz, railW, 1.0f, depth);

                for (float ry : rungYs) {
                    spawnPart(block, material, locKey, 1 * PX, ry, tz, rungW, rungH, depth);
                }
            }
            case SOUTH -> {
                float tz = 0;

                spawnPart(block, material, locKey, 2 * PX, 0, tz, railW, 1.0f, depth);
                spawnPart(block, material, locKey, 12 * PX, 0, tz, railW, 1.0f, depth);

                for (float ry : rungYs) {
                    spawnPart(block, material, locKey, 1 * PX, ry, tz, rungW, rungH, depth);
                }
            }
            case WEST -> {
                float tx = 1f - depth;

                spawnPart(block, material, locKey, tx, 0, 2 * PX, depth, 1.0f, railW);
                spawnPart(block, material, locKey, tx, 0, 12 * PX, depth, 1.0f, railW);

                for (float ry : rungYs) {
                    spawnPart(block, material, locKey, tx, ry, 1 * PX, depth, rungH, rungW);
                }
            }
            case EAST -> {
                float tx = 0;

                spawnPart(block, material, locKey, tx, 0, 2 * PX, depth, 1.0f, railW);
                spawnPart(block, material, locKey, tx, 0, 12 * PX, depth, 1.0f, railW);

                for (float ry : rungYs) {
                    spawnPart(block, material, locKey, tx, ry, 1 * PX, depth, rungH, rungW);
                }
            }
            default -> {
            }
        }
    }

    private void buildPressurePlateDecoration(Block block, Material material) {
        removeDecorationsAt(block);
        float inset = PX;
        float size = 1f - 2 * inset;
        float height = PX;
        BlockData data = block.getBlockData();
        if (data instanceof Powerable powerable && powerable.isPowered()) {

            height = PX / 2f;
        }
        spawnPart(block, material, inset, 0, inset, size, height, size);
    }

    private void buildCarpetDecoration(Block block, Material material) {
        removeDecorationsAt(block);
        spawnPart(block, material, 0, 0, 0, 1.0f, PX, 1.0f);
    }

    private void buildCauldronDecoration(Block block, Material material) {
        removeDecorationsAt(block);
        String locKey = encodeBlockLocation(block);

        spawnPart(block, material, locKey, 2 * PX, 3 * PX, 2 * PX, 12 * PX, 1 * PX, 12 * PX);

        spawnPart(block, material, locKey, 0, 0, 0, 4 * PX, 3 * PX, 2 * PX);
        spawnPart(block, material, locKey, 0, 0, 2 * PX, 2 * PX, 3 * PX, 2 * PX);
        spawnPart(block, material, locKey, 12 * PX, 0, 0, 4 * PX, 3 * PX, 2 * PX);
        spawnPart(block, material, locKey, 14 * PX, 0, 2 * PX, 2 * PX, 3 * PX, 2 * PX);
        spawnPart(block, material, locKey, 0, 0, 14 * PX, 4 * PX, 3 * PX, 2 * PX);
        spawnPart(block, material, locKey, 0, 0, 12 * PX, 2 * PX, 3 * PX, 2 * PX);
        spawnPart(block, material, locKey, 12 * PX, 0, 14 * PX, 4 * PX, 3 * PX, 2 * PX);
        spawnPart(block, material, locKey, 14 * PX, 0, 12 * PX, 2 * PX, 3 * PX, 2 * PX);

        spawnPart(block, material, locKey, 0, 3 * PX, 0, 2 * PX, 13 * PX, 16 * PX);
        spawnPart(block, material, locKey, 14 * PX, 3 * PX, 0, 2 * PX, 13 * PX, 16 * PX);

        spawnPart(block, material, locKey, 2 * PX, 3 * PX, 0, 12 * PX, 13 * PX, 2 * PX);
        spawnPart(block, material, locKey, 2 * PX, 3 * PX, 14 * PX, 12 * PX, 13 * PX, 2 * PX);
    }

    private void buildButtonDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Switch button))
            return;

        removeDecorationsAt(block);

        float d = (button.isPowered() ? 1.05f : 2.05f) * PX;
        float w = 6 * PX;
        float l = 4 * PX;

        float tx = 0, ty = 0, tz = 0, sx = w, sy = l, sz = d;

        FaceAttachable.AttachedFace face = button.getAttachedFace();
        BlockFace facing = button.getFacing();

        if (face == FaceAttachable.AttachedFace.FLOOR) {
            sx = w;
            sy = d;
            sz = l;
            tx = 0.5f - sx / 2f;
            ty = 0;
            tz = 0.5f - sz / 2f;
            if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
                sx = l;
                sz = w;
                tx = 0.5f - sx / 2f;
                tz = 0.5f - sz / 2f;
            }
        } else if (face == FaceAttachable.AttachedFace.CEILING) {
            sx = w;
            sy = d;
            sz = l;
            tx = 0.5f - sx / 2f;
            ty = 1f - d;
            tz = 0.5f - sz / 2f;
            if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
                sx = l;
                sz = w;
                tx = 0.5f - sx / 2f;
                tz = 0.5f - sz / 2f;
            }
        } else {
            switch (facing) {
                case NORTH -> {
                    sx = w;
                    sy = l;
                    sz = d;
                    tx = 0.5f - sx / 2f;
                    ty = 0.5f - sy / 2f;
                    tz = 1f - sz;
                }
                case SOUTH -> {
                    sx = w;
                    sy = l;
                    sz = d;
                    tx = 0.5f - sx / 2f;
                    ty = 0.5f - sy / 2f;
                    tz = 0;
                }
                case WEST -> {
                    sx = d;
                    sy = l;
                    sz = w;
                    tx = 1f - sx;
                    ty = 0.5f - sy / 2f;
                    tz = 0.5f - sz / 2f;
                }
                case EAST -> {
                    sx = d;
                    sy = l;
                    sz = w;
                    tx = 0;
                    ty = 0.5f - sy / 2f;
                    tz = 0.5f - sz / 2f;
                }
                default -> {
                }
            }
        }

        spawnPart(block, material, encodeBlockLocation(block), tx, ty, tz, sx, sy, sz);
    }

    private void buildPotDecoration(Block block, Material material) {
        removeDecorationsAt(block);
        String locKey = encodeBlockLocation(block);

        if (block.getType() == Material.DECORATED_POT) {

            spawnPart(block, material, locKey, 1 * PX, 0, 1 * PX, 14 * PX, 14 * PX, 14 * PX);

            spawnPart(block, material, locKey, 4 * PX, 14 * PX, 4 * PX, 8 * PX, 2 * PX, 8 * PX);
        } else {

            spawnPart(block, material, locKey, 5 * PX, 0, 5 * PX, 6 * PX, 6 * PX, 6 * PX);
        }
    }


    private void buildDecoration(Block block, Material material) {
        BlockData data = block.getBlockData();

        if (isVerticalSlab(block)) {
            removeDecorationsAt(block);
            buildVerticalSlabAllSidesNoRemove(block, material);
            return;
        }
        if (isMixedSlab(block)) {
            removeDecorationsAt(block);
            buildMixedSlabAllHalvesNoRemove(block, material);
            return;
        }

        if (data instanceof Door) {
            buildDoorDecoration(block, material);
        } else if (data instanceof Fence) {
            buildFenceDecoration(block, material);
        } else if (data instanceof Gate) {
            buildGateDecoration(block, material);
        } else if (data instanceof Stairs) {
            buildStairsDecoration(block, material);
        } else if (data instanceof Slab) {
            buildSlabDecoration(block, material);
        } else if (data instanceof TrapDoor) {
            buildTrapdoorDecoration(block, material);
        } else if (data instanceof Ladder) {
            buildLadderDecoration(block, material);
        } else if (data instanceof Switch) {
            buildButtonDecoration(block, material);
        } else {
            Material type = block.getType();
            if (isPressurePlate(type)) {
                buildPressurePlateDecoration(block, material);
            } else if (isCarpet(type)) {
                buildCarpetDecoration(block, material);
            } else if (isCauldron(type)) {
                buildCauldronDecoration(block, material);
            } else if (isPot(type)) {
                buildPotDecoration(block, material);
            }
        }
    }

    private void rebuildIfDecorated(Block block) {
        List<BlockDisplay> displays = findDecorationsAt(block);
        if (displays.isEmpty()) {
            return;
        }

        Set<DecorationSkin> skins = new LinkedHashSet<>();
        for (BlockDisplay d : displays) {
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            String matName = getDecorationMaterialString(pdc);
            if (matName == null) {
                continue;
            }
            Material mat = Material.getMaterial(matName);
            if (mat == null) {
                continue;
            }
            String partKey = getDecorationPartString(pdc);
            skins.add(new DecorationSkin(mat, partKey));
        }
        if (skins.isEmpty()) {
            return;
        }

        removeDecorations(displays);
        for (DecorationSkin skin : skins) {
            if (skin.partKey() == null) {
                if (isVerticalSlab(block)) {
                    buildVerticalSlabAllSidesNoRemove(block, skin.material());
                } else if (isMixedSlab(block)) {
                    buildMixedSlabAllHalvesNoRemove(block, skin.material());
                } else {
                    buildDecoration(block, skin.material());
                }
            } else {
                if (skin.partKey().startsWith(PART_VERTICAL) && !isVerticalSlab(block)) {
                    continue;
                }
                if (skin.partKey().startsWith(PART_MIXED) && !isMixedSlab(block)) {
                    continue;
                }
                applyDecorationPart(block, skin.material(), skin.partKey());
            }
        }
    }

    private void scheduleRebuild(Block block) {
        int x = block.getX(), y = block.getY(), z = block.getZ();
        Location loc = block.getLocation().clone();
        FoliaScheduler.runRegionLater(plugin, loc, 1L, () -> {
            var world = loc.getWorld();
            if (world != null) {
                rebuildIfDecorated(world.getBlockAt(x, y, z));
            }
        });
    }

    private void scheduleRebuildDoor(Block block) {
        int x = block.getX(), y = block.getY(), z = block.getZ();
        Location loc = block.getLocation().clone();
        FoliaScheduler.runRegionLater(plugin, loc, 1L, () -> {
            var world = loc.getWorld();
            if (world == null)
                return;

            Block b = world.getBlockAt(x, y, z);
            if (b.getBlockData() instanceof Door)
                rebuildIfDecorated(b);

            Block above = world.getBlockAt(x, y + 1, z);
            if (above.getBlockData() instanceof Door)
                rebuildIfDecorated(above);

            Block below = world.getBlockAt(x, y - 1, z);
            if (below.getBlockData() instanceof Door)
                rebuildIfDecorated(below);
        });
    }

    private void scheduleRebuildDoorWithAdjacent(Block block) {
        int x = block.getX(), y = block.getY(), z = block.getZ();
        Location loc = block.getLocation().clone();
        FoliaScheduler.runRegionLater(plugin, loc, 1L, () -> {
            var world = loc.getWorld();
            if (world == null)
                return;

            Block b = world.getBlockAt(x, y, z);
            Block bottom = b;
            if (b.getBlockData() instanceof Door door && door.getHalf() == Door.Half.TOP) {
                bottom = b.getRelative(BlockFace.DOWN);
            }

            if (bottom.getBlockData() instanceof Door)
                rebuildIfDecorated(bottom);

            Block top = bottom.getRelative(BlockFace.UP);
            if (top.getBlockData() instanceof Door)
                rebuildIfDecorated(top);

            for (BlockFace face : HORIZONTAL) {
                Block adj = bottom.getRelative(face);
                if (adj.getBlockData() instanceof Door) {
                    rebuildIfDecorated(adj);
                    Block adjTop = adj.getRelative(BlockFace.UP);
                    if (adjTop.getBlockData() instanceof Door)
                        rebuildIfDecorated(adjTop);
                }
            }
        });
    }

    private void scheduleRebuildFenceArea(Block block) {
        int x = block.getX(), y = block.getY(), z = block.getZ();
        Location loc = block.getLocation().clone();
        FoliaScheduler.runRegionLater(plugin, loc, 1L, () -> {
            var world = loc.getWorld();
            if (world == null)
                return;

            Block center = world.getBlockAt(x, y, z);
            rebuildIfDecorated(center);

            for (BlockFace face : HORIZONTAL) {
                rebuildIfDecorated(center.getRelative(face));
            }
        });
    }

    private void scheduleOrphanCleanup(Block block) {
        int x = block.getX(), y = block.getY(), z = block.getZ();
        Location loc = block.getLocation().clone();
        FoliaScheduler.runRegionLater(plugin, loc, 1L, () -> {
            var world = loc.getWorld();
            if (world != null) {
                cleanupOrphanedDecoration(world.getBlockAt(x, y, z));
            }
        });
    }

    private void scheduleCustomSlabDecorationRefresh(Location location) {
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        Location loc = location.clone();
        FoliaScheduler.runRegionLater(plugin, loc, 1L, () -> {
            var world = loc.getWorld();
            if (world == null)
                return;

            Block block = world.getBlockAt(x, y, z);
            if (findDecorationsAt(block).isEmpty())
                return;

            BlockData data = block.getBlockData();
            if (isValidDecoratedTarget(block)) {
                rebuildIfDecorated(block);
            } else {
                cleanupOrphanedDecoration(block);
            }
        });
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player))
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (!player.isSneaking())
            return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null)
            return;
        if (!ModuleAccess.canBuild(plugin, player, clickedBlock.getLocation()))
            return;

        BlockData data = clickedBlock.getBlockData();
        Material type = clickedBlock.getType();
        if (!isDecoratable(data, type) && !isVerticalSlab(clickedBlock) && !isMixedSlab(clickedBlock))
            return;

        ItemStack itemInHand = event.getItem();
        if (itemInHand == null)
            return;
        if (!isVanillaDecorationItem(itemInHand))
            return;
        Material handMat = itemInHand.getType();

        event.setCancelled(true);

        String customSlabPart = null;
        if (isVerticalSlab(clickedBlock)) {
            customSlabPart = PART_VERTICAL + resolveVerticalDecorationSide(player, clickedBlock);
        } else if (isMixedSlab(clickedBlock)) {
            customSlabPart = PART_MIXED + resolveMixedDecorationHalf(player, clickedBlock);
        }

        if (customSlabPart != null) {
            removeDecorationPartsForApply(clickedBlock, customSlabPart);
            applyDecorationPart(clickedBlock, handMat, customSlabPart);
        } else {
            Material oldMat = null;
            List<BlockDisplay> existing = findDecorationsAt(clickedBlock);
            String oldName = getDecorationMaterialName(existing);

            if (oldName != null) {
                oldMat = Material.getMaterial(oldName);
            } else if (data instanceof Door door) {
                Block otherHalf = door.getHalf() == Bisected.Half.TOP
                        ? clickedBlock.getRelative(BlockFace.DOWN)
                        : clickedBlock.getRelative(BlockFace.UP);
                oldMat = getDecorationMaterial(otherHalf);
            }

            if (oldMat != null) {
                clickedBlock.getWorld().dropItemNaturally(
                        clickedBlock.getLocation().add(0.5, 1.0, 0.5),
                        new ItemStack(oldMat, 1));
            }

            buildDecoration(clickedBlock, handMat);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        }
    }

    



    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractDecorationRefresh(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        BlockData data = block.getBlockData();
        if (data instanceof Door) {
            scheduleRebuildDoorWithAdjacent(block);
        } else if (data instanceof Gate || data instanceof TrapDoor) {
            scheduleRebuild(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        BlockData data = block.getBlockData();

        if (data instanceof Door) {
            scheduleRebuildDoor(block);
        } else if (data instanceof Gate) {
            scheduleRebuild(block);
        } else if (data instanceof TrapDoor) {
            scheduleRebuild(block);
        } else if (data instanceof Switch) {
            scheduleRebuild(block);
        } else if (isPressurePlate(block.getType())) {
            scheduleRebuild(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        BlockData data = block.getBlockData();
        Material type = block.getType();

        if (isFenceLike(data)) {
            List<BlockDisplay> decs = findDecorationsAt(block);
            if (!decs.isEmpty()) {
                scheduleRebuildFenceArea(block);
            }
        } else if (data instanceof Door) {
            scheduleOrphanCleanup(block);
        } else if (isPressurePlate(type) || isCarpet(type) || isButton(type) || isPot(type) || data instanceof Ladder) {
            scheduleOrphanCleanup(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();

        if (!findDecorationsAt(placed).isEmpty()) {
            scheduleOrphanCleanup(placed);
        }

        for (BlockFace face : HORIZONTAL) {
            Block adj = placed.getRelative(face);
            if (isFenceLike(adj.getBlockData()) && !findDecorationsAt(adj).isEmpty()) {
                scheduleRebuildFenceArea(adj);
                return;
            }
        }

        if (isFenceLike(placed.getBlockData()) && !findDecorationsAt(placed).isEmpty()) {
            scheduleRebuildFenceArea(placed);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        List<BlockDisplay> decorations = findDecorationsAt(brokenBlock);
        scheduleOrphanCleanup(brokenBlock.getRelative(BlockFace.UP));
        scheduleOrphanCleanup(brokenBlock.getRelative(BlockFace.UP, 2));

        if (!decorations.isEmpty()) {
            dropDecorationItemsForRemovedDisplays(brokenBlock, decorations);
            removeDecorations(decorations);

            BlockData data = brokenBlock.getBlockData();
            if (data instanceof Door door) {
                Block otherHalf = door.getHalf() == Bisected.Half.TOP
                        ? brokenBlock.getRelative(BlockFace.DOWN)
                        : brokenBlock.getRelative(BlockFace.UP);
                removeDecorationsAt(otherHalf);
            }
        }

        for (BlockFace face : HORIZONTAL) {
            Block adj = brokenBlock.getRelative(face);
            if (isFenceLike(adj.getBlockData()) && !findDecorationsAt(adj).isEmpty()) {
                scheduleRebuildFenceArea(adj);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block to = event.getToBlock();
        Material type = to.getType();
        BlockData data = to.getBlockData();
        if (isPressurePlate(type) || isCarpet(type) || isCauldron(type) || isButton(type) || isPot(type)
                || data instanceof Ladder) {
            scheduleOrphanCleanup(to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            List<BlockDisplay> displays = findDecorationsAt(block);
            if (displays.isEmpty())
                continue;

            dropDecorationItemsForRemovedDisplays(block, displays);
            removeDecorations(displays);

            BlockData data = block.getBlockData();
            if (data instanceof Door door) {
                Block otherHalf = door.getHalf() == Bisected.Half.TOP
                        ? block.getRelative(BlockFace.DOWN)
                        : block.getRelative(BlockFace.UP);
                removeDecorationsAt(otherHalf);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCustomSlabDisplayAdded(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof BlockDisplay display))
            return;

        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (!hasVerticalSlabMarker(pdc) && !hasMixedSlabMarker(pdc))
            return;

        scheduleCustomSlabDecorationRefresh(display.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCustomSlabDisplayRemoved(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof BlockDisplay display))
            return;

        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (!hasVerticalSlabMarker(pdc) && !hasMixedSlabMarker(pdc))
            return;

        scheduleCustomSlabDecorationRefresh(display.getLocation());
    }
}
