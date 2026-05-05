package ir.buddy.mint.module.impl;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.ModuleAccess;
import org.bukkit.Art;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class PaintingScrollModule implements Module, Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Art> currentArtMap = new HashMap<>();

    public PaintingScrollModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Painting Scroll";
    }

    @Override
    public String getConfigPath() {
        return "modules.painting-scroll";
    }

    @Override
    public String getDescription() {
        return "Scroll paintings to cycle fitting variants.";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        currentArtMap.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) return;
        if (!player.isSneaking()) return;

        Painting painting = getLookedAtPainting(player);
        if (painting == null) return;
        if (!ModuleAccess.canBuild(plugin, player, painting.getLocation())) return;

        event.setCancelled(true);

        int previousSlot = event.getPreviousSlot();
        int newSlot = event.getNewSlot();
        boolean forward = isScrollForward(previousSlot, newSlot);

        List<Art> fitting = getFittingVariants(painting);
        if (fitting.isEmpty()) return;

        Art current = painting.getArt();
        int currentIndex = fitting.indexOf(current);
        if (currentIndex == -1) currentIndex = 0;

        int nextIndex;
        if (forward) {
            nextIndex = (currentIndex + 1) % fitting.size();
        } else {
            nextIndex = (currentIndex - 1 + fitting.size()) % fitting.size();
        }

        Art nextArt = fitting.get(nextIndex);
        painting.setArt(nextArt, true);

        player.playSound(player.getLocation(), Sound.ENTITY_PAINTING_PLACE, 0.7f, 1.2f);
    }

    private Painting getLookedAtPainting(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                6.0,
                0.2,
                entity -> entity instanceof Painting
        );
        if (result == null || result.getHitEntity() == null) return null;
        Entity hit = result.getHitEntity();
        return (hit instanceof Painting) ? (Painting) hit : null;
    }

    private boolean isScrollForward(int previousSlot, int newSlot) {


        int diff = newSlot - previousSlot;
        if (diff == 1 || diff == -8) return true;
        if (diff == -1 || diff == 8) return false;

        return diff > 0;
    }

    private List<Art> getFittingVariants(Painting painting) {
        BlockFace facing = painting.getFacing();
        Art originalArt = painting.getArt();


        int maxWidth = probeWallDimension(painting, facing, true);
        int maxHeight = probeWallDimension(painting, facing, false);

        List<Art> fitting = new ArrayList<>();
        for (Art art : Art.values()) {
            int artWidthBlocks = art.getBlockWidth();
            int artHeightBlocks = art.getBlockHeight();
            if (artWidthBlocks <= maxWidth && artHeightBlocks <= maxHeight) {

                if (canPlace(painting, art, facing)) {
                    fitting.add(art);
                }
            }
        }


        fitting.sort(Comparator
                .comparingInt((Art a) -> a.getBlockWidth() * a.getBlockHeight())
                .thenComparing(Art::name));

        return fitting;
    }

    private boolean canPlace(Painting painting, Art art, BlockFace facing) {
        Art original = painting.getArt();
        try {
            painting.setArt(art, true);

            if (!painting.isValid() || painting.isDead()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {

            if (painting.isValid() && !painting.isDead()) {
                painting.setArt(original, true);
            }
        }
    }

    private int probeWallDimension(Painting painting, BlockFace facing, boolean horizontal) {
        org.bukkit.Location loc = painting.getLocation().getBlock().getLocation();
        BlockFace positiveDir;
        BlockFace negativeDir;

        if (horizontal) {

            switch (facing) {
                case NORTH:
                    positiveDir = BlockFace.WEST;
                    negativeDir = BlockFace.EAST;
                    break;
                case SOUTH:
                    positiveDir = BlockFace.EAST;
                    negativeDir = BlockFace.WEST;
                    break;
                case WEST:
                    positiveDir = BlockFace.SOUTH;
                    negativeDir = BlockFace.NORTH;
                    break;
                case EAST:
                    positiveDir = BlockFace.NORTH;
                    negativeDir = BlockFace.SOUTH;
                    break;
                default:
                    return 1;
            }
        } else {
            positiveDir = BlockFace.UP;
            negativeDir = BlockFace.DOWN;
        }

        Block wallBehind = painting.getLocation().getBlock().getRelative(facing.getOppositeFace());
        int count = 1;


        Block check = wallBehind;
        for (int i = 0; i < 8; i++) {
            check = check.getRelative(positiveDir);
            Block front = check.getRelative(facing);
            if (check.getType().isSolid() && !front.getType().isSolid()) {
                count++;
            } else {
                break;
            }
        }


        check = wallBehind;
        for (int i = 0; i < 8; i++) {
            check = check.getRelative(negativeDir);
            Block front = check.getRelative(facing);
            if (check.getType().isSolid() && !front.getType().isSolid()) {
                count++;
            } else {
                break;
            }
        }

        return count;
    }
}
