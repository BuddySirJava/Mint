package ir.buddy.mint.module.impl.building;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CarpetGeometryModule implements Module, Listener {
    private static volatile boolean globalRecipesRegistered = false;

    





    public static void resetRecipeRegistrationState() {
        globalRecipesRegistered = false;
    }

    private final MintPlugin plugin;

    private final NamespacedKey geomKey;
    private final NamespacedKey geomVariantKey;
    private final NamespacedKey geomMatKey;
    private final NamespacedKey geomBlockLocKey;
    private final NamespacedKey itemGeomVariantKey;

    
    private static final String LEGACY_CARPET_GEOM_NAMESPACE = "bukkuark";
    private final NamespacedKey legacyGeomKey;
    private final NamespacedKey legacyGeomVariantKey;
    private final NamespacedKey legacyGeomMatKey;
    private final NamespacedKey legacyGeomBlockLocKey;
    private final NamespacedKey legacyItemGeomVariantKey;

    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    private static final float PX = 1f / 16f;
    private static final float HALF_PAD = 0.001f;
    private static final AxisAngle4f NO_ROTATION = new AxisAngle4f(0, 0, 0, 1);
    private static final int MAX_VARIANTS = 53;
    private static final float DEFAULT_PATTERN_TOP = PX + 0.005f;
    private static final float PRESSURE_PLATE_PATTERN_TOP = PX + 0.0035f;
    private static final float PRESSURE_PLATE_PRESSED_DROP = PX / 2 + 0.0035f;
    private static final float PRESSURE_PLATE_INSET = PX;
    private static final float PRESSURE_PLATE_SCALE = 14f / 16f;
    private static final int DEFAULT_CHUNK_SURFACE_LIMIT = 64;

    private record SurfaceFrame(float xOffset, float yTop, float zOffset, float xzScale) {}

    public CarpetGeometryModule(MintPlugin plugin) {
       this.plugin = plugin;
       this.geomKey = new NamespacedKey(plugin, "carpet_geom");
       this.geomVariantKey = new NamespacedKey(plugin, "carpet_geom_variant");
       this.geomMatKey = new NamespacedKey(plugin, "carpet_geom_mat");
       this.geomBlockLocKey = new NamespacedKey(plugin, "carpet_geom_loc");
       this.itemGeomVariantKey = new NamespacedKey(plugin, "carpet_item_geom_variant");
       this.legacyGeomKey = new NamespacedKey(LEGACY_CARPET_GEOM_NAMESPACE, "carpet_geom");
       this.legacyGeomVariantKey = new NamespacedKey(LEGACY_CARPET_GEOM_NAMESPACE, "carpet_geom_variant");
       this.legacyGeomMatKey = new NamespacedKey(LEGACY_CARPET_GEOM_NAMESPACE, "carpet_geom_mat");
       this.legacyGeomBlockLocKey = new NamespacedKey(LEGACY_CARPET_GEOM_NAMESPACE, "carpet_geom_loc");
       this.legacyItemGeomVariantKey = new NamespacedKey(LEGACY_CARPET_GEOM_NAMESPACE, "carpet_item_geom_variant");
   }

    @Override
    public String getName() {
        return "Carpet Geometry";
    }

    @Override
    public String getConfigPath() {
        return "modules.carpetgeometry";
    }

    @Override
   public String getDescription() {
       return "Use a Stonecutter to craft geometric patterns into your carpets.";
   }

   @Override
   public boolean supportsAsyncPreparation() {
       return true;
   }

   @Override
   public void prepareEnable() {


   }


   public void registerRecipesEarly() {
       ensureGlobalRecipesRegistered();
   }

   @Override
  public void enable() {
      try {
         if (!plugin.isEnabled()) {
             plugin.getLogger().warning("Cannot enable Carpet Geometry - plugin not enabled");
             return;
         }

          Bukkit.getPluginManager().registerEvents(this, plugin);
      } catch (Exception e) {
          plugin.getLogger().severe("Failed to enable Carpet Geometry module: " + e.getMessage());
          e.printStackTrace();
      }
  }

    @Override
   public void disable() {
       try {
           HandlerList.unregisterAll(this);


       } catch (Exception e) {
           plugin.getLogger().warning("Error during Carpet Geometry module disable: " + e.getMessage());
       }
   }


    private static boolean isCarpet(Material m) {
        return Tag.WOOL_CARPETS.isTagged(m) || m == Material.MOSS_CARPET;
    }

    private static boolean isPressurePlate(Material material) {
        return Tag.PRESSURE_PLATES.isTagged(material);
    }

    private static boolean isPatternSurface(Material material) {
        return isCarpet(material) || isPressurePlate(material);
    }

    private SurfaceFrame getSurfaceFrame(Block block) {
        if (isPressurePlate(block.getType())) {
            float yTop = PRESSURE_PLATE_PATTERN_TOP;
            if (isPressurePlatePowered(block)) {
                yTop -= PRESSURE_PLATE_PRESSED_DROP;
            }
            return new SurfaceFrame(
                    PRESSURE_PLATE_INSET,
                    yTop,
                    PRESSURE_PLATE_INSET,
                    PRESSURE_PLATE_SCALE
            );
        }
        return new SurfaceFrame(0f, DEFAULT_PATTERN_TOP, 0f, 1f);
    }

    private boolean isPressurePlatePowered(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Powerable powerable) {
            return powerable.isPowered();
        }
        if (data instanceof AnaloguePowerable analogue) {
            return analogue.getPower() > 0;
        }
        return block.getBlockPower() > 0;
    }

    private Material resolveDisplayMaterial(Block block, List<BlockDisplay> displays) {
        if (displays.isEmpty()) {
            return block.getType();
        }

        PersistentDataContainer pdc0 = displays.get(0).getPersistentDataContainer();
        String materialName = pdc0.get(geomMatKey, PersistentDataType.STRING);
        if (materialName == null) {
            materialName = pdc0.get(legacyGeomMatKey, PersistentDataType.STRING);
        }
        Material storedMaterial = materialName != null ? Material.getMaterial(materialName) : null;
        return storedMaterial != null ? storedMaterial : displays.get(0).getBlock().getMaterial();
    }

    private void schedulePressurePlateRebuild(Block block) {
       if (!plugin.isEnabled()) {
           return;
       }

       org.bukkit.Location loc = block.getLocation().clone();

       FoliaScheduler.runRegionLater(plugin, loc, 1L, () -> {
           if (!plugin.isEnabled()) {
               return;
           }

           var world = loc.getWorld();
           if (world == null) {
               return;
           }

            Block current = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            List<BlockDisplay> geometries = findGeometriesAt(current);
            if (geometries.isEmpty()) {
                return;
            }

            if (!isPatternSurface(current.getType())) {
                removeGeometries(geometries);
                return;
            }

            int variant = getCurrentVariant(geometries);
            Material displayMaterial = resolveDisplayMaterial(current, geometries);
            removeGeometries(geometries);
            buildVariant(current, displayMaterial, variant);
        });
    }

    private void registerStonecutterRecipes() {
        try {
            List<Material> carpets = new ArrayList<>();
            for (Material m : Material.values()) {
                if (isPatternSurface(m)) carpets.add(m);
            }

            for (Material carpet : carpets) {

                for (int i = 1; i <= MAX_VARIANTS; i++) {
                    NamespacedKey recipeKey = new NamespacedKey(plugin, "cg_" + carpet.name().toLowerCase() + "_" + i);
                    try {
                        if (Bukkit.getRecipe(recipeKey) != null) {
                            continue;
                        }

                        ItemStack result = new ItemStack(carpet);
                        ItemMeta meta = result.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName("Patterned Surface (Style " + i + ")");
                            meta.getPersistentDataContainer().set(itemGeomVariantKey, PersistentDataType.INTEGER, i);
                            result.setItemMeta(meta);
                        }

                        StonecuttingRecipe recipe = new StonecuttingRecipe(recipeKey, result, carpet);
                        if (!Bukkit.addRecipe(recipe)) {
                            plugin.getLogger().warning(
                                    "Skipped stonecutter recipe (already present or rejected): " + recipeKey);
                            continue;
                        }

                        if (!recipeKeys.contains(recipeKey)) {
                            recipeKeys.add(recipeKey);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning(
                                "Could not register carpet geometry recipe: " + recipeKey + " — " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register carpet geometry recipes: " + e.getMessage());
        }
    }

    private void ensureGlobalRecipesRegistered() {

       if (!plugin.isEnabled()) {
           return;
       }

       if (globalRecipesRegistered) {
           return;
       }
       synchronized (CarpetGeometryModule.class) {
           if (globalRecipesRegistered) {
               return;
           }
           try {
               registerStonecutterRecipes();
               globalRecipesRegistered = true;
               plugin.getLogger().info("Registered " + recipeKeys.size() + " carpet geometry recipes");
           } catch (Exception e) {
               plugin.getLogger().severe("Failed to register carpet geometry recipes: " + e.getMessage());
               e.printStackTrace();
           }
       }
   }
    private String encodeBlockLocation(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean hasCarpetGeomMarker(PersistentDataContainer pdc) {
        return pdc.has(geomKey, PersistentDataType.BYTE)
                || pdc.has(legacyGeomKey, PersistentDataType.BYTE);
    }

    private String getCarpetGeomBlockLoc(PersistentDataContainer pdc) {
        if (pdc.has(geomBlockLocKey, PersistentDataType.STRING)) {
            return pdc.get(geomBlockLocKey, PersistentDataType.STRING);
        }
        if (pdc.has(legacyGeomBlockLocKey, PersistentDataType.STRING)) {
            return pdc.get(legacyGeomBlockLocKey, PersistentDataType.STRING);
        }
        return null;
    }

    private List<BlockDisplay> findGeometriesAt(Block block) {
        String locKey = encodeBlockLocation(block);
        int bx = block.getX(), by = block.getY(), bz = block.getZ();
        BoundingBox box = new BoundingBox(bx - 0.5, by - 0.5, bz - 0.5, bx + 1.5, by + 1.5, bz + 1.5);

        List<BlockDisplay> results = new ArrayList<>();
        for (Entity entity : block.getWorld().getNearbyEntities(box, e -> e instanceof BlockDisplay)) {
            BlockDisplay display = (BlockDisplay) entity;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (hasCarpetGeomMarker(pdc) && locKey.equals(getCarpetGeomBlockLoc(pdc))) {
                results.add(display);
            }
        }
        return results;
    }

    private void removeGeometries(List<BlockDisplay> displays) {
        for (BlockDisplay display : displays) {
            display.remove();
        }
    }

    private int getCurrentVariant(List<BlockDisplay> displays) {
        if (displays.isEmpty()) return 0;
        PersistentDataContainer pdc = displays.get(0).getPersistentDataContainer();
        Integer v = pdc.get(geomVariantKey, PersistentDataType.INTEGER);
        if (v != null) {
            return v;
        }
        return pdc.getOrDefault(legacyGeomVariantKey, PersistentDataType.INTEGER, 0);
    }

    private void spawnPart(Block block, Material material, int variant, String locKey,
                           float tx, float ty, float tz, float sx, float sy, float sz) {
        Location spawnLoc = block.getLocation();
        SurfaceFrame frame = getSurfaceFrame(block);

        float transformedTx = frame.xOffset() + (tx * frame.xzScale());
        float transformedTz = frame.zOffset() + (tz * frame.xzScale());
        float transformedSx = sx * frame.xzScale();
        float transformedSz = sz * frame.xzScale();
        float transformedTy = frame.yTop() + (ty - DEFAULT_PATTERN_TOP);

        plugin.getDisplayBackendManager().backend().spawnBlockDisplay(spawnLoc, getConfigPath(), display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(
                    new Vector3f(transformedTx - HALF_PAD, transformedTy - HALF_PAD, transformedTz - HALF_PAD),
                    NO_ROTATION,
                    new Vector3f(transformedSx + HALF_PAD * 2, sy + HALF_PAD * 2, transformedSz + HALF_PAD * 2),
                    NO_ROTATION
            ));
            display.setViewRange(0.3f);
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(geomKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(geomVariantKey, PersistentDataType.INTEGER, variant);
            pdc.set(geomMatKey, PersistentDataType.STRING, material.name());
            pdc.set(geomBlockLocKey, PersistentDataType.STRING, locKey);
            display.setPersistent(true);
            display.setGravity(false);
        });
    }

    private int getChunkSurfaceLimit() {
        return plugin.getConfig().getInt(getConfigPath() + ".chunk-surface-limit", DEFAULT_CHUNK_SURFACE_LIMIT);
    }

    private int countPatternedSurfacesInChunk(Chunk chunk, String excludedLocKey) {
        Set<String> blockLocations = new HashSet<>();
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof BlockDisplay display)) {
                continue;
            }

            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!hasCarpetGeomMarker(pdc)) {
                continue;
            }

            String storedLoc = getCarpetGeomBlockLoc(pdc);
            if (storedLoc == null || storedLoc.equals(excludedLocKey)) {
                continue;
            }

            blockLocations.add(storedLoc);
        }
        return blockLocations.size();
    }

    private boolean isChunkSurfaceLimitReached(Block block, String locKey) {
        int chunkSurfaceLimit = getChunkSurfaceLimit();
        if (chunkSurfaceLimit <= 0) {
            return false;
        }
        return countPatternedSurfacesInChunk(block.getChunk(), locKey) >= chunkSurfaceLimit;
    }


    private void spawnRotated(Block block, Material mat, int variant, String locKey, float tx, float ty, float tz, float sx, float sy, float sz) {

        spawnPart(block, mat, variant, locKey, tx, ty, tz, sx, sy, sz);

        spawnPart(block, mat, variant, locKey, 16*PX - tz - sz, ty, tx, sz, sy, sx);

        spawnPart(block, mat, variant, locKey, 16*PX - tx - sx, ty, 16*PX - tz - sz, sx, sy, sz);

        spawnPart(block, mat, variant, locKey, tz, ty, 16*PX - tx - sx, sz, sy, sx);
    }


    @EventHandler
   public void onCarpetDye(PlayerInteractEvent event) {
       if (!plugin.isEnabled()) return;

       if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
       if (!event.getPlayer().isSneaking()) return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !isPatternSurface(clickedBlock.getType())) return;
        if (!ModuleAccess.canBuild(plugin, player, clickedBlock.getLocation())) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.getType().name().endsWith("_DYE")) return;

        String colorPrefix = item.getType().name().replace("_DYE", "");
        Material newDisplayMaterial = Material.getMaterial(colorPrefix + "_CARPET");

        if (newDisplayMaterial == null) return;

        List<BlockDisplay> geometries = findGeometriesAt(clickedBlock);

        if (!geometries.isEmpty()) {
            event.setCancelled(true);
            boolean changedAny = false;

            for (BlockDisplay display : geometries) {
                if (display.getBlock().getMaterial() != newDisplayMaterial) {
                    display.setBlock(newDisplayMaterial.createBlockData());
                    display.getPersistentDataContainer().set(geomMatKey, PersistentDataType.STRING, newDisplayMaterial.name());
                    changedAny = true;
                }
            }

            if (changedAny) {
                if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                    item.setAmount(item.getAmount() - 1);
                }
                clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.ITEM_DYE_USE, 1.0f, 1.0f);
            }
        }
    }


    private void buildVariant(Block block, Material mat, int variant) {
        String locKey = encodeBlockLocation(block);
        if (isChunkSurfaceLimitReached(block, locKey)) {
            return;
        }

        float h = DEFAULT_PATTERN_TOP;

        switch (variant) {

            case 1 -> {
                spawnPart(block, mat, variant, locKey, 6*PX, h, 2*PX, 4*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 6*PX, 12*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 4*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 10*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 6*PX, 2*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 6*PX, 2*PX, PX, 4*PX);
            }
            case 2 -> {
                spawnPart(block, mat, variant, locKey, 0*PX, h, 0*PX, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 14*PX, h, 2*PX, 2*PX, PX, 14*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 14*PX, 12*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 4*PX, 2*PX, PX, 10*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 4*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 6*PX, 2*PX, PX, 6*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 10*PX, 4*PX, PX, 2*PX);
            }
            case 3 -> {
                spawnPart(block, mat, variant, locKey, 1*PX, h, 1*PX, 14*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 1*PX, h, 13*PX, 14*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 1*PX, h, 3*PX, 2*PX, PX, 10*PX);
                spawnPart(block, mat, variant, locKey, 13*PX, h, 3*PX, 2*PX, PX, 10*PX);
                spawnPart(block, mat, variant, locKey, 5*PX, h, 5*PX, 6*PX, PX, 6*PX);
            }
            case 4 -> {
                for (int i = 0; i < 8; i++) {
                    float angle = (float) (i * Math.PI / 4);
                    float x = 8 + 5 * (float)Math.cos(angle);
                    float z = 8 + 5 * (float)Math.sin(angle);
                    spawnPart(block, mat, variant, locKey, Math.min(8, x)*PX, h, Math.min(8, z)*PX, Math.abs(x-8)*PX, PX, Math.abs(z-8)*PX);
                }
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }
            case 5 -> {
                spawnPart(block, mat, variant, locKey, 0, h, 0, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 14*PX, h, 2*PX, 2*PX, PX, 14*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 14*PX, 14*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 4*PX, 2*PX, PX, 10*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 4*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 4*PX, 2*PX, PX, 6*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 8*PX, 6*PX, PX, 2*PX);
            }
            case 6 -> {
                spawnRotated(block, mat, variant, locKey, 1*PX, h, 1*PX, 6*PX, PX, 2*PX);
                spawnRotated(block, mat, variant, locKey, 1*PX, h, 3*PX, 2*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }
            case 7 -> {
                for (int i = 0; i < 4; i++) {
                    spawnPart(block, mat, variant, locKey, i*4*PX, h, i*4*PX, 4*PX, PX, 4*PX);
                    spawnPart(block, mat, variant, locKey, (12 - i*4)*PX, h, i*4*PX, 4*PX, PX, 4*PX);
                }
            }
            case 8 -> {
                spawnPart(block, mat, variant, locKey, 2*PX, h, 2*PX, 12*PX, PX, 1*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 13*PX, 12*PX, PX, 1*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 3*PX, 1*PX, PX, 10*PX);
                spawnPart(block, mat, variant, locKey, 13*PX, h, 3*PX, 1*PX, PX, 10*PX);
                spawnRotated(block, mat, variant, locKey, 0, h, 0, 4*PX, PX, 4*PX);
            }
            case 9 -> {
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 2*PX, 6*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }


            case 10 -> {
                spawnPart(block, mat, variant, locKey, 4*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 4*PX, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 10*PX, 16*PX, PX, 2*PX);
            }
            case 11 -> {
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 3*PX, 4*PX, PX, 2*PX);
            }
            case 12 -> {
                for(int i=0; i<3; i++) {
                    float s = (16 - i*6) * PX;
                    spawnPart(block, mat, variant, locKey, i*3*PX, h, i*3*PX, s, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, i*3*PX, h, i*3*PX, 2*PX, PX, s);
                }
            }
            case 13 -> {
                for(int i = 0; i < 4; i++) {
                    spawnPart(block, mat, variant, locKey, 0*PX, h, (i*4)*PX, 8*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, 8*PX, h, (i*4+2)*PX, 8*PX, PX, 2*PX);
                }
            }
            case 14 -> {
                for (int x = 1; x <= 13; x+=4) {
                    for (int z = 1; z <= 13; z+=4) {
                        spawnPart(block, mat, variant, locKey, x*PX, h, z*PX, 2*PX, PX, 2*PX);
                    }
                }
            }
            case 15 -> {
                spawnRotated(block, mat, variant, locKey, 0*PX, h, 0*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 2*PX, 2*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 10*PX, 2*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 4*PX, 2*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 8*PX, 2*PX, PX, 4*PX);
            }
            case 16 -> {
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 2*PX, 5*PX, PX, 1*PX);
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 6*PX, 5*PX, PX, 1*PX);
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 3*PX, 1*PX, PX, 3*PX);
            }
            case 17 -> {
                for(int i=0; i<4; i++){
                    spawnPart(block, mat, variant, locKey, 0, h, (i*4)*PX, 8*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, 8*PX, h, (i*4)*PX, 8*PX, PX, 2*PX);
                }
            }


            case 18 -> {
                spawnPart(block, mat, variant, locKey, 4*PX, h, 0, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 7*PX, h, 2*PX, 2*PX, PX, 6*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 14*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 7*PX, h, 8*PX, 2*PX, PX, 6*PX);
            }
            case 19 -> {
                spawnPart(block, mat, variant, locKey, 0*PX, h, 0*PX, 16*PX, PX, 16*PX);
                spawnPart(block, Material.AIR, variant, locKey, 2*PX, h+0.001f, 2*PX, 12*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h+0.002f, 4*PX, 8*PX, PX, 8*PX);
                spawnPart(block, Material.AIR, variant, locKey, 6*PX, h+0.003f, 6*PX, 4*PX, PX, 4*PX);
            }
            case 20 -> {
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 7*PX, h, 2*PX, 2*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 7*PX, 12*PX, PX, 2*PX);
            }
            case 21 -> {
                spawnPart(block, mat, variant, locKey, 2*PX, h, 2*PX, 12*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h+0.001f, 4*PX, 8*PX, PX, 8*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h+0.002f, 6*PX, 4*PX, PX, 4*PX);
            }
            case 22 -> {
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 0, 4*PX, PX, 2*PX);
                spawnRotated(block, mat, variant, locKey, 4*PX, h, 4*PX, 2*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }
            case 23 -> {
                spawnPart(block, mat, variant, locKey, 6*PX, h, 2*PX, 4*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 6*PX, 12*PX, PX, 4*PX);
                spawnPart(block, Material.AIR, variant, locKey, 6*PX, h+0.001f, 6*PX, 4*PX, PX, 4*PX);
            }
            case 24 -> {
                for (int i = 0; i < 8; i++) {
                    spawnPart(block, mat, variant, locKey, (i*2)*PX, h, i*PX, 2*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, (14-i*2)*PX, h, (i+8)*PX, 2*PX, PX, 2*PX);
                }
            }
            case 25 -> {
                for (int i = 0; i < 8; i++) {
                    spawnPart(block, mat, variant, locKey, i*PX, h, i*PX, (16-2*i)*PX, PX, 2*PX);
                }
            }
            case 26 -> {
                for (int i = 0; i < 3; i++) {
                    spawnPart(block, mat, variant, locKey, 1*PX, h, (1+i*5)*PX, 6*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, 9*PX, h, (1+i*5)*PX, 6*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, 6*PX, h, (3+i*5)*PX, 4*PX, PX, 2*PX);
                }
            }
            case 27 -> {
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 4*PX, 4*PX, PX, 2*PX);
                spawnRotated(block, mat, variant, locKey, 4*PX, h, 2*PX, 2*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }
            case 28 -> {
                for (int x = 0; x < 16; x+=2) {
                    float z = (float) (7 + 5 * Math.sin(x * Math.PI / 16));
                    spawnPart(block, mat, variant, locKey, x*PX, h, z*PX, 2*PX, PX, 2*PX);
                }
            }
            case 29 -> {
                spawnPart(block, mat, variant, locKey, 1*PX, h, 1*PX, 14*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 1*PX, h, 13*PX, 14*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 1*PX, h, 3*PX, 2*PX, PX, 10*PX);
                spawnPart(block, mat, variant, locKey, 13*PX, h, 3*PX, 2*PX, PX, 10*PX);
                spawnPart(block, mat, variant, locKey, 5*PX, h, 5*PX, 6*PX, PX, 1*PX);
                spawnPart(block, mat, variant, locKey, 5*PX, h, 10*PX, 6*PX, PX, 1*PX);
                spawnPart(block, mat, variant, locKey, 5*PX, h, 6*PX, 1*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 6*PX, 1*PX, PX, 4*PX);
            }
            case 30 -> {
                spawnPart(block, mat, variant, locKey, 2*PX, h, 2*PX, 6*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 4*PX, 2*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 8*PX, h, 12*PX, 6*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 12*PX, h, 8*PX, 2*PX, PX, 4*PX);
            }


            case 31 -> {
                for (int i=0; i<8; i++) {
                    spawnRotated(block, mat, variant, locKey, 0, h, i*PX, (8-i)*PX, PX, 1*PX);
                }
            }
            case 32 -> {
                spawnRotated(block, mat, variant, locKey, 0, h, 0, 6*PX, PX, 2*PX);
                spawnRotated(block, mat, variant, locKey, 0, h, 2*PX, 2*PX, PX, 4*PX);
            }
            case 33 -> {
                spawnPart(block, mat, variant, locKey, 0*PX, h, 0*PX, 16*PX, PX, 16*PX);
                spawnPart(block, Material.AIR, variant, locKey, 5*PX, h + 0.001f, 5*PX, 6*PX, PX, 6*PX);
                for(int i=0; i<3; i++){
                    for(int j=0; j<3; j++){
                        if(i==1 && j==1) continue;
                        spawnPart(block, mat, variant, locKey, (i*5+1)*PX, h+0.002f, (j*5+1)*PX, 2*PX, PX, 2*PX);
                    }
                }
            }
            case 34 -> {
                spawnPart(block, mat, variant, locKey, 7*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 7*PX, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 3*PX, h, 3*PX, 10*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 3*PX, h, 11*PX, 10*PX, PX, 2*PX);
            }
            case 35 -> {
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 2*PX, 6*PX, PX, 2*PX);
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 4*PX, 2*PX, PX, 4*PX);
            }
            case 36 -> {
                for (int x = 2; x <= 12; x+=5) {
                    for (int z = 2; z <= 12; z+=5) {
                        spawnPart(block, mat, variant, locKey, x*PX, h, z*PX, 2*PX, PX, 2*PX);
                    }
                }
            }
            case 37 -> {
                spawnPart(block, mat, variant, locKey, 6*PX, h, 2*PX, 4*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 4*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 6*PX, 12*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 10*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 12*PX, 4*PX, PX, 2*PX);
            }
            case 38 -> {
                spawnPart(block, mat, variant, locKey, 2*PX, h, 2*PX, 4*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 2*PX, 4*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }
            case 39 -> {
                spawnPart(block, mat, variant, locKey, 6*PX, h, 2*PX, 4*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 4*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 6*PX, 12*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 12*PX, 4*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 10*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 8*PX, 12*PX, PX, 2*PX);
            }
            case 40 -> {
                spawnPart(block, mat, variant, locKey, 1*PX, h, 1*PX, 14*PX, PX, 1*PX);
                spawnPart(block, mat, variant, locKey, 1*PX, h, 14*PX, 14*PX, PX, 1*PX);
                spawnPart(block, mat, variant, locKey, 1*PX, h, 2*PX, 1*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 14*PX, h, 2*PX, 1*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }
            case 41 -> {
                spawnRotated(block, mat, variant, locKey, 2*PX, h, 2*PX, 3*PX, PX, 3*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 6*PX, 4*PX, PX, 4*PX);
            }
            case 42 -> {
                spawnPart(block, mat, variant, locKey, 7*PX, h, 7*PX, 2*PX, PX, 2*PX);
                for(int i=0; i<16; i++){
                    spawnPart(block, mat, variant, locKey, (float)(8 + 7*Math.cos(i*Math.PI/8))*PX, h, (float)(8 + 3*Math.sin(i*Math.PI/8))*PX, 1*PX, PX, 1*PX);
                    spawnPart(block, mat, variant, locKey, (float)(8 + 3*Math.cos(i*Math.PI/8))*PX, h, (float)(8 + 7*Math.sin(i*Math.PI/8))*PX, 1*PX, PX, 1*PX);
                    spawnPart(block, mat, variant, locKey, (float)(8 + 6*Math.cos(i*Math.PI/8 + Math.PI/4))*PX, h, (float)(8 + 6*Math.sin(i*Math.PI/8 + Math.PI/4))*PX, 1*PX, PX, 1*PX);
                }
            }
            case 43 -> {
                for(int i=0; i<4; i++){
                    spawnPart(block, mat, variant, locKey, (i*4)*PX, h, 0, 2*PX, PX, 16*PX);
                    spawnPart(block, mat, variant, locKey, 0, h, (i*4+2)*PX, 16*PX, PX, 2*PX);
                }
            }
            case 44 -> {
                spawnPart(block, mat, variant, locKey, 4*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 4*PX, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 10*PX, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 7*PX, h, 7*PX, 2*PX, PX, 2*PX);
            }
            case 45 -> {
                spawnPart(block, mat, variant, locKey, 2*PX, h, 2*PX, 12*PX, PX, 12*PX);
                spawnPart(block, Material.AIR, variant, locKey, 4*PX, h+0.001f, 4*PX, 8*PX, PX, 8*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h+0.002f, 6*PX, 4*PX, PX, 4*PX);
            }
            case 46 -> {
                spawnRotated(block, mat, variant, locKey, 0*PX, h, 0*PX, 4*PX, PX, 4*PX);
                spawnRotated(block, mat, variant, locKey, 4*PX, h, 4*PX, 4*PX, PX, 4*PX);
            }
            case 47 -> {
                for(int i=0; i<8; i++) {
                    spawnPart(block, mat, variant, locKey, i*PX, h, i*PX, 2*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, (14-i)*PX, h, i*PX, 2*PX, PX, 2*PX);
                }
            }
            case 48 -> {
                spawnPart(block, mat, variant, locKey, 5*PX, h, 2*PX, 6*PX, PX, 12*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 5*PX, 12*PX, PX, 6*PX);
            }
            case 49 -> {
                for (int i = 0; i < 8; i++) {
                    spawnPart(block, mat, variant, locKey, (8 - i)*PX, h, (8 - i)*PX, (i*2)*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, (8 - i)*PX, h, (6 + i)*PX, (i*2)*PX, PX, 2*PX);
                }
            }
            case 50 -> {
                spawnPart(block, mat, variant, locKey, 2*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 0, 2*PX, PX, 16*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 4*PX, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 8*PX, 16*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 0, h, 12*PX, 16*PX, PX, 2*PX);
            }
            case 51 -> {
                for(int x=4; x<12; x+=4) {
                    for(int z=4; z<12; z+=4) {
                        spawnPart(block, mat, variant, locKey, x*PX, h, z*PX, 2*PX, PX, 2*PX);
                        spawnPart(block, mat, variant, locKey, (x-2)*PX, h, (z-2)*PX, 2*PX, PX, 2*PX);
                    }
                }
            }
            case 52 -> {
                for(int i=0; i<4; i++){
                    spawnPart(block, mat, variant, locKey, (float)(7-i)*PX, h, (float)(7-i)*PX, (float)(2+2*i)*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, (float)(7-i)*PX, h, (float)(7-i)*PX, 2*PX, PX, (float)(2+2*i)*PX);
                    spawnPart(block, mat, variant, locKey, (float)(7-i)*PX, h, (float)(7+i)*PX, (float)(2+2*i)*PX, PX, 2*PX);
                    spawnPart(block, mat, variant, locKey, (float)(7+i)*PX, h, (float)(7-i)*PX, 2*PX, PX, (float)(2+2*i)*PX);
                }
            }
            case 53 -> {
                spawnPart(block, mat, variant, locKey, 2*PX, h, 4*PX, 4*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 10*PX, h, 4*PX, 4*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 7*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 2*PX, h, 8*PX, 12*PX, PX, 4*PX);
                spawnPart(block, mat, variant, locKey, 4*PX, h, 12*PX, 8*PX, PX, 2*PX);
                spawnPart(block, mat, variant, locKey, 6*PX, h, 14*PX, 4*PX, PX, 2*PX);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent event) {
       if (!plugin.isEnabled()) return;

       Player player = event.getPlayer();
       if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
           return;
       }
       if (!ModuleAccess.canBuild(plugin, player, event.getBlockPlaced().getLocation())) {
           return;
       }

        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta() || !isPatternSurface(event.getBlockPlaced().getType())) return;

        PersistentDataContainer itemPdc = item.getItemMeta().getPersistentDataContainer();
        Integer variantId = itemPdc.get(itemGeomVariantKey, PersistentDataType.INTEGER);
        if (variantId == null) {
            variantId = itemPdc.get(legacyItemGeomVariantKey, PersistentDataType.INTEGER);
        }

        if (variantId != null && variantId > 0) {
            buildVariant(event.getBlockPlaced(), item.getType(), variantId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
       if (!plugin.isEnabled()) return;

       boolean moduleEnabledForPlayer = ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer());
       boolean shouldDropCustomItem = moduleEnabledForPlayer
               && event.isDropItems()
               && event.getPlayer().getGameMode() != GameMode.CREATIVE;
        if (handlePatternBreak(event.getBlock(), shouldDropCustomItem) && shouldDropCustomItem) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityExplode(EntityExplodeEvent event) {
       if (!plugin.isEnabled()) return;


        List<Block> blockListCopy = new ArrayList<>(event.blockList());
        for (Block block : blockListCopy) {
            if (isPatternSurface(block.getType())) {
                if (handlePatternBreak(block, true)) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
       if (!plugin.isEnabled()) return;


        List<Block> blockListCopy = new ArrayList<>(event.blockList());
        for (Block block : blockListCopy) {
            if (isPatternSurface(block.getType())) {
                if (handlePatternBreak(block, true)) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockPhysics(org.bukkit.event.block.BlockPhysicsEvent event) {
       if (!plugin.isEnabled()) return;

       Block block = event.getBlock();
        if (isPressurePlate(block.getType())) {
            if (!findGeometriesAt(block).isEmpty()) {
                schedulePressurePlateRebuild(block);
            }
            return;
        }

        if (isPatternSurface(block.getType())) {
            if (!block.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                if (handlePatternBreak(block, true)) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onRedstone(BlockRedstoneEvent event) {
       if (!plugin.isEnabled()) return;

       Block block = event.getBlock();
        if (!isPressurePlate(block.getType())) {
            return;
        }
        if (findGeometriesAt(block).isEmpty()) {
            return;
        }
        schedulePressurePlateRebuild(block);
    }

    private boolean handlePatternBreak(Block block, boolean dropItem) {
        List<BlockDisplay> geometries = findGeometriesAt(block);

        if (!geometries.isEmpty()) {
            int variant = getCurrentVariant(geometries);
            Material mat = block.getType();

            removeGeometries(geometries);

            if (dropItem) {
                ItemStack customDrop = new ItemStack(mat);
                ItemMeta meta = customDrop.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("Patterned Surface (Style " + variant + ")");
                    meta.getPersistentDataContainer().set(itemGeomVariantKey, PersistentDataType.INTEGER, variant);
                    customDrop.setItemMeta(meta);
                }

                block.getWorld().dropItemNaturally(block.getLocation(), customDrop);
            }
            return true;
        }
        return false;
    }

}
