package ir.buddy.mint.module.impl.interaction;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class InvisibleFrameModule implements Module, Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey colorKey;
    private final NamespacedKey mixCountKey;
    private final NamespacedKey frameDisplayUuidKey;
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

    public InvisibleFrameModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.colorKey = new NamespacedKey(plugin, "dyeable_item_frame_color");
        this.mixCountKey = new NamespacedKey(plugin, "dyeable_item_frame_mix_count");
        this.frameDisplayUuidKey = new NamespacedKey(plugin, "dyeable_item_frame_display_uuid");
    }

    @Override
    public String getName() {
        return "Invisible Frame";
    }

    @Override
    public String getConfigPath() {
        return "modules.invisible-frame";
    }

    @Override
    public String getDescription() {
        return "Toggle item frame visibility with shears.";
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
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) return;
        if (!event.getPlayer().isSneaking()) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame itemFrame)) return;
        if (!ModuleAccess.canBuild(plugin, event.getPlayer(), itemFrame.getLocation())) return;

        if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.SHEARS) return;

        boolean nowVisible = !itemFrame.isVisible();
        itemFrame.setVisible(nowVisible);
        if (!nowVisible) {
            removeDisplayAndDropDye(itemFrame);
        }

        itemFrame.getWorld().playSound(
            itemFrame.getLocation(),
            Sound.ENTITY_SHEEP_SHEAR
            ,
            1.0f,
            1.0f
        );


        event.setCancelled(true);
    }

    private void removeDisplayAndDropDye(ItemFrame frame) {
        PersistentDataContainer pdc = frame.getPersistentDataContainer();
        Integer rgb = pdc.get(colorKey, PersistentDataType.INTEGER);

        String displayUuidRaw = pdc.get(frameDisplayUuidKey, PersistentDataType.STRING);
        if (displayUuidRaw != null) {
            try {
                Entity entity = frame.getWorld().getEntity(UUID.fromString(displayUuidRaw));
                if (entity instanceof ItemDisplay itemDisplay && itemDisplay.isValid()) {
                    itemDisplay.remove();
                }
            } catch (IllegalArgumentException ignored) {
                
            }
            pdc.remove(frameDisplayUuidKey);
        }

        if (rgb != null) {
            Material dye = getClosestDye(Color.fromRGB(rgb));
            frame.getWorld().dropItemNaturally(frame.getLocation(), new ItemStack(dye));
            pdc.remove(colorKey);
            pdc.remove(mixCountKey);
        }
    }

    private Material getClosestDye(Color targetColor) {
        Material bestMatch = Material.WHITE_DYE;
        int minDistanceSq = Integer.MAX_VALUE;
        for (Map.Entry<Material, Color> entry : DYE_COLORS.entrySet()) {
            Color dyeColor = entry.getValue();
            int dr = targetColor.getRed() - dyeColor.getRed();
            int dg = targetColor.getGreen() - dyeColor.getGreen();
            int db = targetColor.getBlue() - dyeColor.getBlue();
            int distanceSq = dr * dr + dg * dg + db * db;
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                bestMatch = entry.getKey();
            }
        }
        return bestMatch;
    }
}
