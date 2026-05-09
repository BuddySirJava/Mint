package ir.buddy.mint.module.impl.building;

import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ModuleAccess;
import ir.buddy.mint.util.ScheduledTaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ReacharoundPlacementModule implements Module, Listener {

    private static final double SURVIVAL_REACH = 4.5D;
    private static final double CREATIVE_REACH = 5.0D;

    private final JavaPlugin plugin;
    private final Map<UUID, VisualizerOutline> visualizers = new HashMap<>();
    private final Map<UUID, ScheduledTaskHandle> visualizerTasks = new HashMap<>();

    private BlockData cachedVisualizerBlockData;
    private Display.Brightness cachedBrightness;
    private float visualizerThickness = 0.008F;
    private Material visualizerMaterial = Material.BLACK_STAINED_GLASS;
    private int brightnessBlock = 15;
    private int brightnessSky = 15;

    private double leniency = 0.5D;
    private List<String> whitelist = new ArrayList<>();
    private List<String> blacklist = new ArrayList<>();

    public ReacharoundPlacementModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String getName() {
        return "Reacharound Placement";
    }

    @Override
    public String getConfigPath() {
        return "modules.reacharound-placement";
    }

    @Override
    public String getDescription() {
        return "Allows Quark-style reacharound placement when normal targeting misses.";
    }

    @Override
    public boolean isEnabledByConfig(org.bukkit.configuration.file.FileConfiguration config) {
        String path = getConfigPath();
        if (config.contains(path + ".enabled")) {
            return config.getBoolean(path + ".enabled", true);
        }
        return config.getBoolean("modules.bedrock-bridging.enabled", true);
    }

    @Override
    public boolean defaultOnFirstJoin(org.bukkit.configuration.file.FileConfiguration config) {
        String path = getConfigPath();
        if (config.contains(path + ".default-on-first-join")) {
            return config.getBoolean(path + ".default-on-first-join", false);
        }
        return config.getBoolean("modules.bedrock-bridging.default-on-first-join", false);
    }

    @Override
    public void enable() {
        loadConfiguration();
        updateCachedValues();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runEntity(plugin, player, () -> {
                createVisualizer(player);
                startVisualizerTask(player);
            });
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        for (ScheduledTaskHandle handle : visualizerTasks.values()) {
            handle.cancel();
        }
        visualizerTasks.clear();
        for (VisualizerOutline outline : visualizers.values()) {
            if (outline != null) {
                outline.remove();
            }
        }
        visualizers.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        createVisualizer(player);
        startVisualizerTask(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        stopVisualizerTask(uuid);
        VisualizerOutline outline = visualizers.remove(uuid);
        if (outline != null) {
            outline.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        FoliaScheduler.runEntity(plugin, event.getPlayer(), () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                createVisualizer(player);
            }
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        createVisualizer(event.getPlayer());
    }

    private void loadConfiguration() {
        String base = getConfigPath();
        plugin.getConfig().addDefault(base + ".enabled", true);
        plugin.getConfig().addDefault(base + ".default-on-first-join", false);
        plugin.getConfig().addDefault(base + ".leniency", 0.5D);
        plugin.getConfig().addDefault(base + ".whitelist", new ArrayList<String>());
        plugin.getConfig().addDefault(base + ".blacklist", new ArrayList<String>());
        plugin.getConfig().addDefault(base + ".visualizer.thickness", 0.008D);
        plugin.getConfig().addDefault(base + ".visualizer.material", "BLACK_STAINED_GLASS");
        plugin.getConfig().addDefault(base + ".visualizer.brightness.block", 15);
        plugin.getConfig().addDefault(base + ".visualizer.brightness.sky", 15);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        leniency = clamp(plugin.getConfig().getDouble(base + ".leniency", 0.5D), 0.0D, 1.0D);
        whitelist = normalizeIdList(plugin.getConfig().getStringList(base + ".whitelist"));
        blacklist = normalizeIdList(plugin.getConfig().getStringList(base + ".blacklist"));

        visualizerThickness = (float) clamp(plugin.getConfig().getDouble(base + ".visualizer.thickness", 0.008D), 0.001D, 0.25D);
        String materialName = plugin.getConfig().getString(base + ".visualizer.material", "BLACK_STAINED_GLASS");
        Material parsedMaterial = Material.matchMaterial(materialName == null ? "BLACK_STAINED_GLASS" : materialName);
        if (parsedMaterial == null || !parsedMaterial.isBlock()) {
            parsedMaterial = Material.BLACK_STAINED_GLASS;
        }
        visualizerMaterial = parsedMaterial;
        brightnessBlock = clampInt(plugin.getConfig().getInt(base + ".visualizer.brightness.block", 15), 0, 15);
        brightnessSky = clampInt(plugin.getConfig().getInt(base + ".visualizer.brightness.sky", 15), 0, 15);
    }

    private void updateCachedValues() {
        cachedVisualizerBlockData = visualizerMaterial.createBlockData();
        cachedBrightness = new Display.Brightness(brightnessBlock, brightnessSky);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        if (event.useItemInHand() == Event.Result.DENY && event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        Player player = event.getPlayer();
        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        ReacharoundTarget target = getPlayerReacharoundTarget(player);
        if (target == null || hand != target.hand()) {
            return;
        }

        ItemStack stack = player.getInventory().getItem(hand);
        if (!isReacharoundItem(stack)) {
            return;
        }
        if (!ModuleAccess.canBuild(plugin, player, target.targetBlock().getLocation())) {
            return;
        }
        if (!isReplaceable(target.targetBlock())) {
            return;
        }

        Block support = getSupportBlock(target.targetBlock());
        if (support == null) {
            return;
        }
        if (!placeBlock(player, hand, stack, target.targetBlock(), support)) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
    }

    private ReacharoundTarget getPlayerReacharoundTarget(Player player) {
        EquipmentSlot hand = resolveHand(player);
        if (hand == null) {
            return null;
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double reach = getReach(player);
        World world = player.getWorld();

        RayTraceResult normalHit = world.rayTraceBlocks(eye, direction, reach, FluidCollisionMode.NEVER, true);
        if (normalHit != null && normalHit.getHitBlock() != null) {
            return null;
        }

        ReacharoundTarget vertical = getVerticalTarget(player, hand, eye, direction, reach);
        if (vertical != null) {
            return vertical;
        }
        return getHorizontalTarget(player, hand, eye, direction, reach);
    }

    private ReacharoundTarget getVerticalTarget(Player player, EquipmentSlot hand, Location eye, Vector direction, double reach) {
        if (player.getLocation().getPitch() < 0.0F) {
            return null;
        }

        Location offsetStart = eye.clone().add(0.0D, leniency, 0.0D);
        RayTraceResult hit = player.getWorld().rayTraceBlocks(offsetStart, direction, reach, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            return null;
        }

        Block target = hit.getHitBlock().getRelative(BlockFace.DOWN);
        if (isReplaceable(target) && (player.getLocation().getY() - target.getY() > 1.0D)) {
            return new ReacharoundTarget(target, BlockFace.DOWN, hand);
        }
        return null;
    }

    private ReacharoundTarget getHorizontalTarget(Player player, EquipmentSlot hand, Location eye, Vector direction, double reach) {
        BlockFace facing = toHorizontalBlockFace(player.getLocation().getYaw());
        Location offsetStart = eye.clone().add(-leniency * facing.getModX(), 0.0D, -leniency * facing.getModZ());
        RayTraceResult hit = player.getWorld().rayTraceBlocks(offsetStart, direction, reach, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            return null;
        }

        Block target = hit.getHitBlock().getRelative(facing);
        if (isReplaceable(target)) {
            return new ReacharoundTarget(target, facing.getOppositeFace(), hand);
        }
        return null;
    }

    private void startVisualizerTask(Player player) {
        UUID id = player.getUniqueId();
        if (visualizerTasks.containsKey(id)) {
            return;
        }
        ScheduledTaskHandle handle = FoliaScheduler.runEntityAtFixedRate(
                plugin,
                player,
                1L,
                1L,
                () -> tickVisualizerForPlayer(player),
                () -> visualizerTasks.remove(id)
        );
        visualizerTasks.put(id, handle);
    }

    private void stopVisualizerTask(UUID playerId) {
        ScheduledTaskHandle handle = visualizerTasks.remove(playerId);
        if (handle != null) {
            handle.cancel();
        }
    }

    private void tickVisualizerForPlayer(Player player) {
        if (!player.isValid() || !player.isOnline()) {
            return;
        }

        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            VisualizerOutline outline = visualizers.get(player.getUniqueId());
            if (outline != null) {
                outline.hide();
            }
            return;
        }

        VisualizerOutline outline = visualizers.get(player.getUniqueId());
        if (outline == null || !outline.isValid(player.getWorld())) {
            createVisualizer(player);
            outline = visualizers.get(player.getUniqueId());
            if (outline == null) {
                return;
            }
        }

        if (!isEligibleForVisualizer(player)) {
            outline.hide();
            return;
        }

        ReacharoundTarget target = getPlayerReacharoundTarget(player);
        if (target == null
                || !ModuleAccess.canBuild(plugin, player, target.targetBlock().getLocation())
                || !isReplaceable(target.targetBlock())
                || getSupportBlock(target.targetBlock()) == null) {
            outline.hide();
            return;
        }

        outline.showAt(target.targetBlock());
    }

    private void createVisualizer(Player player) {
        UUID uuid = player.getUniqueId();
        VisualizerOutline old = visualizers.remove(uuid);
        if (old != null) {
            old.remove();
        }
        visualizers.put(uuid, new VisualizerOutline(player));
    }

    private boolean isEligibleForVisualizer(Player player) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return resolveHand(player) != null;
    }

    private EquipmentSlot resolveHand(Player player) {
        if (isReacharoundItem(player.getInventory().getItemInMainHand())) {
            return EquipmentSlot.HAND;
        }
        if (isReacharoundItem(player.getInventory().getItemInOffHand())) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private boolean isReacharoundItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String id = stack.getType().getKey().toString();
        if (blacklist.contains(id)) {
            return false;
        }
        return stack.getType().isBlock() || whitelist.contains(id);
    }

    private Block getSupportBlock(Block target) {
        BlockFace[] faces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP };
        for (BlockFace face : faces) {
            Block neighbor = target.getRelative(face);
            if (!isReplaceable(neighbor)) {
                return neighbor;
            }
        }
        return null;
    }

    private boolean placeBlock(Player player, EquipmentSlot hand, ItemStack sourceItem, Block targetBlock, Block supportBlock) {
        if (!isReplaceable(targetBlock)) {
            return false;
        }

        ItemStack live = player.getInventory().getItem(hand);
        if (live == null || live.getType() != sourceItem.getType() || !live.getType().isBlock()) {
            return false;
        }

        BlockState replacedState = targetBlock.getState();
        targetBlock.setType(sourceItem.getType(), false);

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                targetBlock,
                replacedState,
                supportBlock,
                live,
                player,
                true,
                hand
        );
        Bukkit.getPluginManager().callEvent(placeEvent);

        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            targetBlock.setBlockData(replacedState.getBlockData(), false);
            return false;
        }

        if (hand == EquipmentSlot.HAND) {
            player.swingMainHand();
        } else {
            player.swingOffHand();
        }

        targetBlock.getWorld().playSound(
                targetBlock.getLocation().add(0.5D, 0.5D, 0.5D),
                Sound.BLOCK_STONE_PLACE,
                SoundCategory.BLOCKS,
                1.0F,
                1.0F
        );

        if (player.getGameMode() != GameMode.CREATIVE) {
            int amount = live.getAmount();
            if (amount <= 1) {
                player.getInventory().setItem(hand, null);
            } else {
                live.setAmount(amount - 1);
            }
        }
        return true;
    }

    private boolean isReplaceable(Block block) {
        return block.isEmpty() || block.isPassable();
    }

    private double getReach(Player player) {
        return player.getGameMode() == GameMode.CREATIVE ? CREATIVE_REACH : SURVIVAL_REACH;
    }

    private BlockFace toHorizontalBlockFace(float yaw) {
        int index = Math.floorMod(Math.round(yaw / 90.0F), 4);
        return switch (index) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    private static List<String> normalizeIdList(List<String> raw) {
        List<String> normalized = new ArrayList<>(raw.size());
        for (String id : raw) {
            if (id == null) {
                continue;
            }
            String trimmed = id.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ReacharoundTarget(Block targetBlock, BlockFace hitFace, EquipmentSlot hand) {
    }

    private final class VisualizerOutline {
        private final Player player;
        private final BlockDisplay[] edges = new BlockDisplay[12];
        private final Location baseLocation = new Location(null, 0.0D, 0.0D, 0.0D);
        private final Location teleportLocation = new Location(null, 0.0D, 0.0D, 0.0D);
        private Block currentTarget;

        private VisualizerOutline(Player player) {
            this.player = player;
            World world = player.getWorld();
            Location spawnLocation = player.getLocation();

            for (int i = 0; i < edges.length; i++) {
                edges[i] = world.spawn(spawnLocation, BlockDisplay.class, entity -> {
                    entity.setBlock(cachedVisualizerBlockData);
                    entity.setBrightness(cachedBrightness);
                    entity.setVisibleByDefault(false);
                    entity.setShadowRadius(0.0F);
                    entity.setShadowStrength(0.0F);
                    entity.setPersistent(false);
                    entity.setInvulnerable(true);
                    Transformation transformation = entity.getTransformation();
                    transformation.getScale().set(0.0F, 0.0F, 0.0F);
                    entity.setTransformation(transformation);
                });
                player.showEntity(plugin, edges[i]);
            }
        }

        private boolean isValid(World world) {
            return edges[0] != null
                    && edges[0].isValid()
                    && !edges[0].isDead()
                    && edges[0].getWorld().equals(world);
        }

        private void remove() {
            for (BlockDisplay edge : edges) {
                safeRemoveEdge(edge);
            }
            currentTarget = null;
        }

        private void safeRemoveEdge(BlockDisplay edge) {
            if (edge == null || edge.isDead()) {
                return;
            }
            try {
                FoliaScheduler.runEntity(plugin, edge, edge::remove);
            } catch (Throwable ignored) {
                try {
                    edge.remove();
                } catch (Throwable ignoredAgain) {
                }
            }
        }

        private void hide() {
            if (currentTarget == null) {
                return;
            }
            Location safeLocation = player.isOnline() ? player.getLocation() : edges[0].getLocation();
            for (BlockDisplay edge : edges) {
                if (edge == null || edge.isDead()) {
                    continue;
                }
                Transformation transformation = edge.getTransformation();
                transformation.getScale().set(0.0F, 0.0F, 0.0F);
                edge.setTransformation(transformation);
                edge.teleportAsync(safeLocation);
            }
            currentTarget = null;
        }

        private void showAt(Block target) {
            if (currentTarget != null && currentTarget.equals(target)) {
                return;
            }
            for (BlockDisplay edge : edges) {
                if (edge == null || edge.isDead()) {
                    return;
                }
            }

            currentTarget = target;
            float t = visualizerThickness;
            float length = 1.0F + t;
            float outer = -t / 2.0F;
            float inner = 1.0F + outer;

            target.getLocation(baseLocation);
            World baseWorld = baseLocation.getWorld();
            double baseX = baseLocation.getX();
            double baseY = baseLocation.getY();
            double baseZ = baseLocation.getZ();

            updateEdge(edges[0], baseWorld, baseX + outer, baseY + outer, baseZ + outer, length, t, t);
            updateEdge(edges[1], baseWorld, baseX + outer, baseY + outer, baseZ + inner, length, t, t);
            updateEdge(edges[2], baseWorld, baseX + outer, baseY + inner, baseZ + outer, length, t, t);
            updateEdge(edges[3], baseWorld, baseX + outer, baseY + inner, baseZ + inner, length, t, t);
            updateEdge(edges[4], baseWorld, baseX + outer, baseY + outer, baseZ + outer, t, length, t);
            updateEdge(edges[5], baseWorld, baseX + inner, baseY + outer, baseZ + outer, t, length, t);
            updateEdge(edges[6], baseWorld, baseX + outer, baseY + outer, baseZ + inner, t, length, t);
            updateEdge(edges[7], baseWorld, baseX + inner, baseY + outer, baseZ + inner, t, length, t);
            updateEdge(edges[8], baseWorld, baseX + outer, baseY + outer, baseZ + outer, t, t, length);
            updateEdge(edges[9], baseWorld, baseX + inner, baseY + outer, baseZ + outer, t, t, length);
            updateEdge(edges[10], baseWorld, baseX + outer, baseY + inner, baseZ + outer, t, t, length);
            updateEdge(edges[11], baseWorld, baseX + inner, baseY + inner, baseZ + outer, t, t, length);
        }

        private void updateEdge(BlockDisplay edge, World world, double x, double y, double z, float scaleX, float scaleY, float scaleZ) {
            if (edge == null || edge.isDead()) {
                return;
            }

            teleportLocation.setWorld(world);
            teleportLocation.setX(x);
            teleportLocation.setY(y);
            teleportLocation.setZ(z);
            edge.teleportAsync(teleportLocation);

            Transformation transformation = edge.getTransformation();
            transformation.getScale().set(scaleX, scaleY, scaleZ);
            edge.setTransformation(transformation);

            if (!edge.getBlock().equals(cachedVisualizerBlockData)) {
                edge.setBlock(cachedVisualizerBlockData);
            }
            edge.setBrightness(cachedBrightness);
        }

        private boolean owns(Entity entity) {
            for (BlockDisplay edge : edges) {
                if (edge != null && edge.equals(entity)) {
                    return true;
                }
            }
            return false;
        }
    }
}
