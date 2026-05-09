package ir.buddy.mint.module.impl.building;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public final class SignItemsModule implements Module, Listener {
    private static final Vector3f DISPLAY_SCALE = new Vector3f(0.5f, 0.5f, 0.5f);

    private final NamespacedKey displayUuidKey;
    private final NamespacedKey itemMarkerKey;
    private final MintPlugin plugin;

    public SignItemsModule(MintPlugin plugin) {
        this.plugin = plugin;
        this.displayUuidKey = new NamespacedKey(plugin, "sign_item_display_uuid");
        this.itemMarkerKey = new NamespacedKey(plugin, "sign_item_marker");
    }

    @Override
    public String getName() {
        return "Sign Items";
    }

    @Override
    public String getConfigPath() {
        return "modules.sign-items";
    }

    @Override
    public String getDescription() {
        return "Place items on hanging signs using display entities.";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof TileState tileState)) {
            return;
        }

        BlockData data = block.getBlockData();
        if (!(data instanceof HangingSign) && !(data instanceof WallHangingSign)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking() || !ModuleAccess.isEnabledForPlayer(plugin, this, player) || !ModuleAccess.canBuild(plugin, player, block.getLocation())) {
            return;
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        
        
        if (pdc.has(displayUuidKey, PersistentDataType.STRING)) {
            removeItemFromSign(block, tileState, pdc);
            event.setCancelled(true);
            return;
        }

        
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.isEmpty()) {
            return;
        }

        ItemStack displayItem = heldItem.clone();
        displayItem.setAmount(1);

        boolean spawned = spawnDisplayOnSign(block, tileState, pdc, displayItem);
        if (!spawned) {
            return;
        }

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            heldItem.subtract(1);
        }
        
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof TileState tileState) {
            PersistentDataContainer pdc = tileState.getPersistentDataContainer();
            if (pdc.has(displayUuidKey, PersistentDataType.STRING)) {
                removeItemFromSign(block, tileState, pdc);
            }
        }
    }

    private boolean spawnDisplayOnSign(Block block, TileState tileState, PersistentDataContainer pdc, ItemStack item) {
        Location loc = getDisplayLocation(block);
        if (loc == null) {
            return false;
        }
        ItemDisplay display = plugin.getDisplayBackendManager().backend().spawnItemDisplay(loc, getConfigPath(), d -> {
            d.setPersistent(true);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setItemStack(item);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    DISPLAY_SCALE,
                    new Quaternionf()
            ));
            
            d.getPersistentDataContainer().set(itemMarkerKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (display == null) {
            return false;
        }

        pdc.set(displayUuidKey, PersistentDataType.STRING, display.getUniqueId().toString());
        tileState.update();
        return true;
    }

    private void removeItemFromSign(Block block, TileState tileState, PersistentDataContainer pdc) {
        String uuidString = pdc.get(displayUuidKey, PersistentDataType.STRING);
        if (uuidString != null) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                Entity entity = block.getWorld().getEntity(uuid);
                if (entity instanceof ItemDisplay display) {
                    if (display.getItemStack() != null) {
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), display.getItemStack());
                    }
                    display.remove();
                }
            } catch (IllegalArgumentException ignored) {}
        }
        
        pdc.remove(displayUuidKey);
        tileState.update();
    }

    private Location getDisplayLocation(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        BlockData data = block.getBlockData();
        
        float yOffset = 0.0f;
        float forwardOffset = 0.2f;
        BlockFace face = BlockFace.NORTH;

        if (data instanceof WallHangingSign wallSign) {
            face = wallSign.getFacing();
            forwardOffset = 0.2f;
            yOffset = -0.15f; 
        } 
        
        else if (data instanceof HangingSign hangingSign) {
            face = hangingSign.getRotation();
            forwardOffset = 0.2f;
            yOffset = -0.15f;
        } else {
            return null;
        }

        Vector dir = face.getDirection();
        
        center.add(dir.multiply(0.08f)); 
        
        center.add(0, yOffset, 0);
        center.setDirection(dir);

        return center;
    }
}
