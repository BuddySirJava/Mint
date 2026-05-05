package ir.buddy.mint.module.impl;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import ir.buddy.mint.util.ScheduledTaskHandle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LeashDecorationModule implements Module, Listener {

    private static final String TAG = "leash_decoration";
    private static final double MAX_DISTANCE = 20.0;
    private static final double SWING_ROPE_LENGTH = 7.0;

    private final JavaPlugin plugin;
    private final Map<UUID, Wolf> pendingLeash = new HashMap<>();
    private ScheduledTaskHandle swingTask;

    public LeashDecorationModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Leash Decoration";
    }

    @Override
    public String getConfigPath() {
        return "modules.leash-decoration";
    }

    @Override
    public String getDescription() {
        return "Link fences with leads and swing from them.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);


        swingTask = FoliaScheduler.runGlobalAtFixedRate(plugin, 1L, 1L, () -> {
            for (Map.Entry<UUID, Wolf> entry : pendingLeash.entrySet()) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                Wolf anchor = entry.getValue();
                if (player == null || anchor == null || anchor.isDead()) {
                    continue;
                }
                FoliaScheduler.runEntity(plugin, player, () -> applySwingPhysics(player, anchor));
            }
        });
    }

    @Override
    public void disable() {
        if (swingTask != null) swingTask.cancel();

        for (Wolf wolf : pendingLeash.values()) {
            removeWolf(wolf);
        }
        pendingLeash.clear();
        HandlerList.unregisterAll(this);
    }

    private void applySwingPhysics(Player player, Wolf anchor) {
        if (player.isOnGround()) {
            return;
        }

        Location playerLoc = player.getLocation();
        Location anchorLoc = anchor.getLocation();

        if (!playerLoc.getWorld().equals(anchorLoc.getWorld())) {
            return;
        }

        Vector playerToAnchor = anchorLoc.toVector().subtract(playerLoc.toVector());
        double dist = playerToAnchor.length();

        if (dist > SWING_ROPE_LENGTH) {
            Vector velocity = player.getVelocity();
            Vector normalizedRope = playerToAnchor.clone().normalize();

            double dot = velocity.dot(normalizedRope);

            if (dot < 0) {
                velocity.subtract(normalizedRope.multiply(dot));
            }

            Vector pull = normalizedRope.multiply((dist - SWING_ROPE_LENGTH) * 0.15);
            player.setVelocity(velocity.add(pull));
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;
        if (event.isSneaking() && pendingLeash.containsKey(player.getUniqueId()) && !player.isOnGround()) {

            cancelPending(player);
        }
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() != Material.LEAD) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || !Tag.FENCES.isTagged(clicked.getType())) return;
        if (!ModuleAccess.canBuild(plugin, player, clicked.getLocation())) return;

        event.setCancelled(true);

        UUID playerId = player.getUniqueId();
        Location fenceLoc = clicked.getLocation();


        Wolf existing = findCompletedWolfAt(fenceLoc);
        if (existing != null && !pendingLeash.containsValue(existing)) {
            detachAndReattachToPlayer(existing, fenceLoc, player);
            pendingLeash.put(playerId, existing);
            player.getInventory().removeItem(new ItemStack(Material.LEAD, 1));
            return;
        }

        if (!pendingLeash.containsKey(playerId)) {

            Wolf wolf = spawnInvisibleWolf(fenceLoc);
            wolf.setLeashHolder(player);
            fenceLoc.getWorld().spawnEntity(fenceLoc, EntityType.LEASH_KNOT);
            pendingLeash.put(playerId, wolf);
            return;
        }


        Wolf wolf = pendingLeash.remove(playerId);
        if (wolf == null || wolf.isDead()) return;

        Location wolfLoc = wolf.getLocation().getBlock().getLocation();

        if (!wolfLoc.getWorld().equals(fenceLoc.getWorld())
                || wolfLoc.distance(fenceLoc) > MAX_DISTANCE
                || wolfLoc.equals(fenceLoc)) {
            cancelPending(player);
            return;
        }

        LeashHitch hitch = (LeashHitch) fenceLoc.getWorld().spawnEntity(fenceLoc, EntityType.LEASH_KNOT);
        wolf.setLeashHolder(hitch);

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        }
    }

    private void detachAndReattachToPlayer(Wolf wolf, Location clickedFence, Player player) {
        Location wolfBlock = wolf.getLocation().getBlock().getLocation();
        Location clickedBlock = clickedFence.getBlock().getLocation();

        if (wolfBlock.equals(clickedBlock)) {
            if (wolf.isLeashed() && wolf.getLeashHolder() instanceof LeashHitch hitch) {
                Location otherFence = hitch.getLocation().getBlock().getLocation();
                wolf.setLeashHolder(null);
                wolf.remove();
                Wolf newWolf = spawnInvisibleWolf(otherFence);
                newWolf.setLeashHolder(player);
                pendingLeash.values().remove(wolf);
                pendingLeash.put(player.getUniqueId(), newWolf);
            }
        } else {
            wolf.setLeashHolder(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) return;
        if (event.getRightClicked() instanceof Wolf wolf && wolf.getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeashEntity(PlayerLeashEntityEvent event) {
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, event.getPlayer())) return;
        if (event.getEntity() instanceof Wolf wolf && wolf.getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityUnleash(EntityUnleashEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf)) return;
        if (!wolf.getScoreboardTags().contains(TAG)) return;

        pendingLeash.values().remove(wolf);
        FoliaScheduler.runEntity(plugin, wolf, () -> removeWolf(wolf));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Tag.FENCES.isTagged(block.getType())) return;
        Location blockLoc = block.getLocation();
        FoliaScheduler.runRegion(plugin, blockLoc, () -> cleanupWolvesAt(blockLoc));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof LeashHitch)) return;
        Location hitchLoc = event.getEntity().getLocation().getBlock().getLocation();
        FoliaScheduler.runRegion(plugin, hitchLoc, () -> cleanupWolvesAt(hitchLoc));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer());
    }

    private void cleanupWolvesAt(Location blockLoc) {
        for (Entity entity : blockLoc.getWorld().getNearbyEntities(blockLoc.clone().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (!(entity instanceof Wolf wolf) || !wolf.getScoreboardTags().contains(TAG)) continue;

            if (wolf.getLocation().getBlock().getLocation().equals(blockLoc)) {
                pendingLeash.values().remove(wolf);
                removeWolf(wolf);
                continue;
            }

            if (wolf.isLeashed() && wolf.getLeashHolder() instanceof LeashHitch hitch) {
                if (hitch.getLocation().getBlock().getLocation().equals(blockLoc)) {
                    pendingLeash.values().remove(wolf);
                    removeWolf(wolf);
                }
            }
        }
    }

    private Wolf findCompletedWolfAt(Location fenceLoc) {
        Location blockLoc = fenceLoc.getBlock().getLocation();

        for (Entity entity : fenceLoc.getWorld().getNearbyEntities(fenceLoc.clone().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (!(entity instanceof Wolf wolf) || !wolf.getScoreboardTags().contains(TAG)) continue;
            if (!wolf.isLeashed() || !(wolf.getLeashHolder() instanceof LeashHitch)) continue;

            if (wolf.getLocation().getBlock().getLocation().equals(blockLoc)) return wolf;

            if (wolf.getLeashHolder() instanceof LeashHitch hitch
                    && hitch.getLocation().getBlock().getLocation().equals(blockLoc)) {
                return wolf;
            }
        }
        return null;
    }

    private void cancelPending(Player player) {
        Wolf wolf = pendingLeash.remove(player.getUniqueId());
        if (wolf != null) {
            removeWolf(wolf);
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                player.getInventory().addItem(new ItemStack(Material.LEAD, 1));
            }
        }
    }

    private Wolf spawnInvisibleWolf(Location fenceLocation) {
        Location spawnLoc = fenceLocation.clone().add(0.5, 0.25, 0.5);

        Wolf wolf = (Wolf) fenceLocation.getWorld().spawnEntity(spawnLoc, EntityType.WOLF);
        wolf.setTamed(true);
        wolf.setSilent(true);
        wolf.setInvulnerable(true);
        wolf.setAI(false);
        wolf.setGravity(false);
        wolf.setInvisible(true);
        wolf.setCollidable(false);
        wolf.setSitting(true);
        wolf.setPersistent(true);
        wolf.setBaby();
        wolf.setAgeLock(true);
        wolf.addScoreboardTag(TAG);

        return wolf;
    }

    private void removeWolf(Wolf wolf) {
        if (wolf == null || wolf.isDead()) return;
        if (wolf.isLeashed()) {
            wolf.setLeashHolder(null);
        }
        wolf.remove();
    }
}
