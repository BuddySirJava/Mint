package ir.buddy.mint.module.impl.building;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class DyeableItemFramesModule implements Module, Listener {
    private static final Vector3f DISPLAY_SCALE = new Vector3f(1.3f, 1.3f, 0.002f);
    private static final float DISPLAY_OFFSET = 0.01f;
    private static final Color DEFAULT_FRAME_COLOR = Color.fromRGB(160, 110, 80);

    private final NamespacedKey colorKey;
    private final NamespacedKey mixCountKey;
    private final NamespacedKey displayMarkerKey;
    private final NamespacedKey displayFrameUuidKey;
    private final NamespacedKey frameDisplayUuidKey;
    private final MintPlugin plugin;

    private static final Map<Material, Color> DYE_COLORS = new EnumMap<>(Material.class);

    static {
        DYE_COLORS.put(Material.WHITE_DYE, Color.fromRGB(249, 255, 254));
        DYE_COLORS.put(Material.ORANGE_DYE, Color.fromRGB(249, 128, 29));
        DYE_COLORS.put(Material.MAGENTA_DYE, Color.fromRGB(199, 78, 189));
        DYE_COLORS.put(Material.LIGHT_BLUE_DYE, Color.fromRGB(58, 179, 218));
        DYE_COLORS.put(Material.YELLOW_DYE, Color.fromRGB(254, 216, 61));
        DYE_COLORS.put(Material.LIME_DYE, Color.fromRGB(128, 199, 31));
        DYE_COLORS.put(Material.PINK_DYE, Color.fromRGB(243, 139, 170));
        DYE_COLORS.put(Material.GRAY_DYE, Color.fromRGB(71, 79, 82));
        DYE_COLORS.put(Material.LIGHT_GRAY_DYE, Color.fromRGB(157, 157, 151));
        DYE_COLORS.put(Material.CYAN_DYE, Color.fromRGB(22, 156, 156));
        DYE_COLORS.put(Material.PURPLE_DYE, Color.fromRGB(137, 50, 184));
        DYE_COLORS.put(Material.BLUE_DYE, Color.fromRGB(60, 68, 170));
        DYE_COLORS.put(Material.BROWN_DYE, Color.fromRGB(131, 84, 50));
        DYE_COLORS.put(Material.GREEN_DYE, Color.fromRGB(94, 124, 22));
        DYE_COLORS.put(Material.RED_DYE, Color.fromRGB(176, 46, 38));
        DYE_COLORS.put(Material.BLACK_DYE, Color.fromRGB(29, 29, 33));
    }

    public DyeableItemFramesModule(MintPlugin plugin) {
        this.plugin = plugin;
        this.colorKey = new NamespacedKey(plugin, "dyeable_item_frame_color");
        this.mixCountKey = new NamespacedKey(plugin, "dyeable_item_frame_mix_count");
        this.displayMarkerKey = new NamespacedKey(plugin, "dyeable_item_frame_display");
        this.displayFrameUuidKey = new NamespacedKey(plugin, "dyeable_item_frame_owner");
        this.frameDisplayUuidKey = new NamespacedKey(plugin, "dyeable_item_frame_display_uuid");
    }

    @Override
    public String getName() {
        return "Dyeable Item Frames";
    }

    @Override
    public String getConfigPath() {
        return "modules.dyeable-item-frames";
    }

    @Override
    public String getDescription() {
        return "Dye item frames using mixed dye combinations, displaying a wool texture overlay.";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractWithItemFrame(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking() || !ModuleAccess.isEnabledForPlayer(plugin, this, player) || !ModuleAccess.canBuild(plugin, player, frame.getLocation())) {
            return;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (isDye(heldItem)) {
            Color dyeColor = colorForDye(heldItem.getType());
            if (dyeColor != null) {
                applyDye(frame, dyeColor);
                updateDisplay(frame);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    heldItem.subtract();
                }
                event.setCancelled(true);
            }
            return;
        }

        if (heldItem.getType() == Material.WATER_BUCKET) {
            if (removeDye(frame)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            removeLinkedDisplay(frame);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            removeLinkedDisplay(frame);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ItemFrame frame && frame.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
                updateDisplay(frame);
            }
        }
    }

    private void applyDye(ItemFrame frame, Color newDyeColor) {
        PersistentDataContainer pdc = frame.getPersistentDataContainer();

        Color currentColor = pdc.has(colorKey, PersistentDataType.INTEGER)
                ? Color.fromRGB(pdc.get(colorKey, PersistentDataType.INTEGER))
                : DEFAULT_FRAME_COLOR;
        int mixCount = pdc.getOrDefault(mixCountKey, PersistentDataType.INTEGER, 0);

        int totalDyes = mixCount + 1;
        int red = (currentColor.getRed() * mixCount + newDyeColor.getRed()) / totalDyes;
        int green = (currentColor.getGreen() * mixCount + newDyeColor.getGreen()) / totalDyes;
        int blue = (currentColor.getBlue() * mixCount + newDyeColor.getBlue()) / totalDyes;

        pdc.set(colorKey, PersistentDataType.INTEGER, Color.fromRGB(red, green, blue).asRGB());
        pdc.set(mixCountKey, PersistentDataType.INTEGER, totalDyes);
    }

    private boolean removeDye(ItemFrame frame) {
        PersistentDataContainer pdc = frame.getPersistentDataContainer();
        if (!pdc.has(colorKey, PersistentDataType.INTEGER)) {
            return false;
        }

        pdc.remove(colorKey);
        pdc.remove(mixCountKey);
        removeLinkedDisplay(frame);
        return true;
    }

    private void updateDisplay(ItemFrame frame) {
        ItemDisplay display = findOrSpawnDisplay(frame);
        if (display != null) {
            refreshDisplay(frame, display);
        }
    }

    private ItemDisplay findOrSpawnDisplay(ItemFrame frame) {
        ItemDisplay existing = findLinkedDisplay(frame);
        if (existing != null) {
            return existing;
        }
        if (!frame.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
            return null;
        }
        return spawnDisplay(frame);
    }

    private ItemDisplay spawnDisplay(ItemFrame frame) {
        Location location = frame.getLocation();
        if (!location.getChunk().isLoaded()) {
            return null;
        }

        Vector facing = frame.getFacing().getDirection();
        Location displayLocation = location.clone().add(facing.clone().multiply(DISPLAY_OFFSET));
        displayLocation.setDirection(facing);
        if (!plugin.getDisplayEntityController().canSpawn(displayLocation, getConfigPath())) {
            return null;
        }

        ItemDisplay display = frame.getWorld().spawn(displayLocation, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    DISPLAY_SCALE,
                    new Quaternionf()
            ));
            PersistentDataContainer displayPdc = d.getPersistentDataContainer();
            displayPdc.set(displayMarkerKey, PersistentDataType.BYTE, (byte) 1);
            displayPdc.set(displayFrameUuidKey, PersistentDataType.STRING, frame.getUniqueId().toString());
            plugin.getDisplayEntityController().markManaged(d, getConfigPath());
        });

        frame.getPersistentDataContainer().set(frameDisplayUuidKey, PersistentDataType.STRING, display.getUniqueId().toString());
        return display;
    }

    private void refreshDisplay(ItemFrame frame, ItemDisplay display) {
        Integer rgb = frame.getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
        if (rgb == null || !frame.isVisible()) {
            removeLinkedDisplay(frame);
            return;
        }

        Material woolType = getClosestWool(Color.fromRGB(rgb));
        display.setItemStack(new ItemStack(woolType));

        Vector facing = frame.getFacing().getDirection();
        Location targetLocation = frame.getLocation().clone().add(facing.clone().multiply(DISPLAY_OFFSET));
        targetLocation.setDirection(facing);
        if (!display.getLocation().equals(targetLocation)) {
            display.teleport(targetLocation);
        }
    }

    private ItemDisplay findLinkedDisplay(ItemFrame frame) {
        String uuidRaw = frame.getPersistentDataContainer().get(frameDisplayUuidKey, PersistentDataType.STRING);
        if (uuidRaw == null) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(uuidRaw);
            Entity entity = frame.getWorld().getEntity(uuid);
            if (entity instanceof ItemDisplay itemDisplay && entity.isValid()) {
                return itemDisplay;
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private void removeLinkedDisplay(ItemFrame frame) {
        ItemDisplay display = findLinkedDisplay(frame);
        if (display != null && display.isValid()) {
            display.remove();
        }
        frame.getPersistentDataContainer().remove(frameDisplayUuidKey);
    }

    private Material getClosestWool(Color targetColor) {
        Material bestMatch = Material.WHITE_WOOL;
        int minDistanceSq = Integer.MAX_VALUE;

        for (Map.Entry<Material, Color> entry : DYE_COLORS.entrySet()) {
            Color dyeColor = entry.getValue();
            int dr = targetColor.getRed() - dyeColor.getRed();
            int dg = targetColor.getGreen() - dyeColor.getGreen();
            int db = targetColor.getBlue() - dyeColor.getBlue();

            int distanceSq = dr * dr + dg * dg + db * db;
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                bestMatch = woolForDye(entry.getKey());
            }
        }
        return bestMatch;
    }

    private Material woolForDye(Material dye) {
        return switch (dye) {
            case WHITE_DYE -> Material.WHITE_WOOL;
            case ORANGE_DYE -> Material.ORANGE_WOOL;
            case MAGENTA_DYE -> Material.MAGENTA_WOOL;
            case LIGHT_BLUE_DYE -> Material.LIGHT_BLUE_WOOL;
            case YELLOW_DYE -> Material.YELLOW_WOOL;
            case LIME_DYE -> Material.LIME_WOOL;
            case PINK_DYE -> Material.PINK_WOOL;
            case GRAY_DYE -> Material.GRAY_WOOL;
            case LIGHT_GRAY_DYE -> Material.LIGHT_GRAY_WOOL;
            case CYAN_DYE -> Material.CYAN_WOOL;
            case PURPLE_DYE -> Material.PURPLE_WOOL;
            case BLUE_DYE -> Material.BLUE_WOOL;
            case BROWN_DYE -> Material.BROWN_WOOL;
            case GREEN_DYE -> Material.GREEN_WOOL;
            case RED_DYE -> Material.RED_WOOL;
            case BLACK_DYE -> Material.BLACK_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }

    private boolean isDye(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getType().name().endsWith("_DYE") && !stack.hasItemMeta();
    }

    private Color colorForDye(Material dye) {
        return DYE_COLORS.get(dye);
    }
}
