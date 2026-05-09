package ir.buddy.mint.module.impl.transport;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import ir.buddy.mint.util.ScheduledTaskHandle;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoatImprovementsModule implements Module, Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, ScheduledTaskHandle> boatTasks = new HashMap<>();

    private static final double ICE_SPEED_MULTIPLIER = 1.15;
    private static final double LAND_SPEED_MULTIPLIER = 0.85;
    
    private static final double WATER_CRUISE_ACCEL = 0.005;
    private static final double WATER_CRUISE_MIN_SPEED_SQ = 0.02 * 0.02;
    private static final double WATER_CRUISE_MAX_SPEED_SQ = 0.48 * 0.48;

    public BoatImprovementsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Boat Improvements";
    }

    @Override
    public String getConfigPath() {
        return "modules.boat-improvements";
    }

    @Override
    public String getDescription() {
        return "Smoother boats: cruise assist on water, ice glide, easier beach slides, less bumping mobs.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        for (ScheduledTaskHandle handle : boatTasks.values()) {
            handle.cancel();
        }
        boatTasks.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoatEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Boat boat && event.getEntered() instanceof Player player) {
            if (ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
                startBoatTask(boat);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoatCollision(VehicleEntityCollisionEvent event) {
        if (event.getVehicle() instanceof Boat boat && hasEnabledPlayerRider(boat)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBoatExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Boat boat))
            return;
        if (!(event.getExited() instanceof Player player))
            return;
        stopBoatTask(boat.getUniqueId());
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player))
            return;

        FoliaScheduler.runEntityLater(plugin, player, 1L, () -> {
            if (player.isOnline()) {
                player.setVelocity(new Vector(0, 0.25, 0));
            }
        });
    }

    private void startBoatTask(Boat boat) {
        UUID id = boat.getUniqueId();
        if (boatTasks.containsKey(id)) {
            return;
        }
        ScheduledTaskHandle handle = FoliaScheduler.runEntityAtFixedRate(
                plugin,
                boat,
                1L,
                1L,
                () -> tickBoatPhysics(id),
                () -> boatTasks.remove(id)
        );
        boatTasks.put(id, handle);
    }

    private void stopBoatTask(UUID boatId) {
        ScheduledTaskHandle handle = boatTasks.remove(boatId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private void tickBoatPhysics(UUID boatId) {
        Entity entity = plugin.getServer().getEntity(boatId);
        if (!(entity instanceof Boat boat) || !boat.isValid()) {
            stopBoatTask(boatId);
            return;
        }

        Material below = boat.getLocation().subtract(0, 0.5, 0).getBlock().getType();
        Vector velocity = boat.getVelocity();

        if (isWaterBelow(below)) {
            applyWaterCruise(boat, velocity);
            return;
        }

        if (isIce(below)) {
            boat.setVelocity(velocity.multiply(new Vector(ICE_SPEED_MULTIPLIER, 1.0, ICE_SPEED_MULTIPLIER)));
        } else if (below.isSolid() && !below.name().contains("WATER")) {
            double horizSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
            if (horizSpeed > 0.02) {
                boat.setVelocity(velocity.multiply(new Vector(LAND_SPEED_MULTIPLIER, 1.0, LAND_SPEED_MULTIPLIER)));
            }
        }
    }

    private void applyWaterCruise(Boat boat, Vector velocity) {
        double vx = velocity.getX();
        double vz = velocity.getZ();
        double hsq = vx * vx + vz * vz;
        if (hsq < WATER_CRUISE_MIN_SPEED_SQ || hsq > WATER_CRUISE_MAX_SPEED_SQ) {
            return;
        }
        double h = Math.sqrt(hsq);
        double nx = vx / h;
        double nz = vz / h;
        boat.setVelocity(new Vector(
                vx + nx * WATER_CRUISE_ACCEL,
                velocity.getY(),
                vz + nz * WATER_CRUISE_ACCEL
        ));
    }

    private boolean isWaterBelow(Material material) {
        return material == Material.WATER
                || material == Material.KELP
                || material == Material.KELP_PLANT
                || material == Material.SEAGRASS
                || material == Material.TALL_SEAGRASS
                || material == Material.BUBBLE_COLUMN;
    }

    private boolean isIce(Material material) {
        return material == Material.ICE
                || material == Material.PACKED_ICE
                || material == Material.BLUE_ICE
                || material == Material.FROSTED_ICE;
    }

    private boolean hasEnabledPlayerRider(Boat boat) {
        for (Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Player player
                    && ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
                return true;
            }
        }
        return false;
    }
}
