package ir.buddy.mint.module.impl.transport;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import ir.buddy.mint.util.ScheduledTaskHandle;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MinecartImprovementsModule implements Module, Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, ScheduledTaskHandle> minecartTasks = new HashMap<>();
    private final Set<UUID> trackedMinecarts = new HashSet<>();

    private static final double SPEED_MULTIPLIER = 1.6;
    private static final double MAX_SPEED = 0.8;
    private static final double LINK_DISTANCE = 3.5;
    private static final double LINK_PULL_STRENGTH = 0.15;

    private final Map<UUID, UUID> linkedCarts = new HashMap<>();

    public MinecartImprovementsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Minecart Improvements";
    }

    @Override
    public String getConfigPath() {
        return "modules.minecart-improvements";
    }

    @Override
    public String getDescription() {
        return "Boost minecart speed and link carts with chains.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        trackedMinecarts.clear();
        for (ScheduledTaskHandle handle : minecartTasks.values()) {
            handle.cancel();
        }
        minecartTasks.clear();
        linkedCarts.clear();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMinecartEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Minecart cart && event.getEntered() instanceof Player player) {
            if (ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
                trackedMinecarts.add(cart.getUniqueId());
                startMinecartTask(cart);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (!(event.getRightClicked() instanceof Minecart clickedCart))
            return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player))
            return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.IRON_CHAIN)
            return;

        event.setCancelled(true);

        UUID clickedId = clickedCart.getUniqueId();

        UUID pendingLink = getPendingLink(player);
        if (pendingLink != null) {
            Entity firstEntity = plugin.getServer().getEntity(pendingLink);
            if (firstEntity instanceof Minecart firstCart && firstCart.isValid()
                    && !firstCart.getUniqueId().equals(clickedId)) {
                linkedCarts.put(firstCart.getUniqueId(), clickedId);
                player.removeMetadata("minecart_link_pending", plugin);
                player.sendMessage("§aMinecarts linked!");

                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }
                return;
            }
            player.removeMetadata("minecart_link_pending", plugin);
        }

        player.setMetadata("minecart_link_pending",
                new org.bukkit.metadata.FixedMetadataValue(plugin, clickedId.toString()));
        player.sendMessage("§eFirst minecart selected. Right-click another minecart with a chain to link.");
    }

    private UUID getPendingLink(Player player) {
        if (!player.hasMetadata("minecart_link_pending"))
            return null;
        try {
            String val = player.getMetadata("minecart_link_pending").get(0).asString();
            return UUID.fromString(val);
        } catch (Exception e) {
            return null;
        }
    }

    private void startMinecartTask(Minecart cart) {
        UUID id = cart.getUniqueId();
        if (minecartTasks.containsKey(id)) {
            return;
        }
        ScheduledTaskHandle handle = FoliaScheduler.runEntityAtFixedRate(
                plugin,
                cart,
                1L,
                1L,
                () -> tickMinecart(id),
                () -> minecartTasks.remove(id)
        );
        minecartTasks.put(id, handle);
    }

    private void stopMinecartTask(UUID cartId) {
        ScheduledTaskHandle handle = minecartTasks.remove(cartId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private void tickMinecart(UUID cartId) {
        Entity entity = plugin.getServer().getEntity(cartId);
        if (!(entity instanceof Minecart cart) || !cart.isValid()) {
            stopMinecartTask(cartId);
            trackedMinecarts.remove(cartId);
            linkedCarts.entrySet().removeIf(e -> e.getKey().equals(cartId) || e.getValue().equals(cartId));
            return;
        }

        if (trackedMinecarts.contains(cartId)) {
            applySpeedBoost(cart);
        }

        UUID backId = linkedCarts.get(cartId);
        if (backId != null) {
            Entity backEntity = plugin.getServer().getEntity(backId);
            if (backEntity instanceof Minecart back && back.isValid()) {
                applyLinkPullOnSchedulers(cart, back);
            } else {
                linkedCarts.remove(cartId);
            }
        }
    }

    private void applySpeedBoost(Minecart cart) {
        Material below = cart.getLocation().subtract(0, 0.3, 0).getBlock().getType();
        if (!isRail(below))
            return;

        cart.setMaxSpeed(MAX_SPEED);
        Vector velocity = cart.getVelocity();
        double speed = velocity.length();
        if (speed > 0.01 && speed < MAX_SPEED) {
            cart.setVelocity(velocity.multiply(SPEED_MULTIPLIER));
        }
    }

    private void applyLinkPullOnSchedulers(Minecart front, Minecart back) {
        if (!front.getWorld().equals(back.getWorld())) {
            linkedCarts.remove(front.getUniqueId());
            return;
        }

        double distance = front.getLocation().distance(back.getLocation());
        if (distance > LINK_DISTANCE * 3) {
            linkedCarts.remove(front.getUniqueId());
            return;
        }

        if (distance > LINK_DISTANCE) {
            Vector direction = front.getLocation().toVector()
                    .subtract(back.getLocation().toVector()).normalize();
            Vector delta = direction.multiply(LINK_PULL_STRENGTH);
            FoliaScheduler.runEntity(plugin, back, () -> {
                if (back.isValid()) {
                    back.setVelocity(back.getVelocity().add(delta));
                }
            });
        } else if (distance < 1.0) {
            Vector push = back.getLocation().toVector()
                    .subtract(front.getLocation().toVector()).normalize();
            Vector delta = push.multiply(0.05);
            FoliaScheduler.runEntity(plugin, back, () -> {
                if (back.isValid()) {
                    back.setVelocity(back.getVelocity().add(delta));
                }
            });
        }
    }

    private boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }
}
