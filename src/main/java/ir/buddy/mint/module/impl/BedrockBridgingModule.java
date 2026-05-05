package ir.buddy.mint.module.impl;

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
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BedrockBridgingModule implements Module, Listener {

    private static final double SURVIVAL_REACH = 4.5D;
    private static final double CREATIVE_REACH = 5.0D;
    private static final float MIN_BRIDGING_PITCH = 10.0F;
    private static final double INTERSECTION_EPSILON = 0.001D;


    private static final BlockFace[] NEIGHBOR_FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
        BlockFace.WEST, BlockFace.DOWN, BlockFace.UP
    };

    private final JavaPlugin plugin;
    private final Map<UUID, VisualizerOutline> visualizers = new HashMap<>();
    private final Map<UUID, ScheduledTaskHandle> visualizerTasks = new HashMap<>();


    private BlockData cachedVisualizerBlockData;
    private Display.Brightness cachedBrightness;

    private float visualizerThickness = 0.025F;
    private Material visualizerMaterial = Material.BLACK_CONCRETE;
    private int brightnessBlock = 15;
    private int brightnessSky = 15;

    private boolean antiChokeEnabled = true;
    private boolean antiChokeAutoCorrect = true;
    private float antiChokeMinPitch = 15.0F;
    private float antiChokeMaxPitch = 70.0F;

    private boolean godbridgeEnabled = true;
    private float godbridgeMinPitch = 75.0F;
    private float godbridgeMaxPitch = 85.0F;
    private double godbridgeMaxDistance = 1.5D;

    public BedrockBridgingModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String getName() {
        return "Bedrock Bridging";
    }

    @Override
    public String getConfigPath() {
        return "modules.bedrock-bridging";
    }

    @Override
    public String getDescription() {
        return "Adds Bedrock-style bridging assistance, placement preview, anti-choke support, and godbridge support.";
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

    private void loadConfiguration() {
        String base = getConfigPath();

        plugin.getConfig().addDefault(base + ".visualizer.thickness", 0.025D);
        plugin.getConfig().addDefault(base + ".visualizer.material", "BLACK_CONCRETE");
        plugin.getConfig().addDefault(base + ".visualizer.brightness.block", 15);
        plugin.getConfig().addDefault(base + ".visualizer.brightness.sky", 15);

        plugin.getConfig().addDefault(base + ".anti-choke.enabled", true);
        plugin.getConfig().addDefault(base + ".anti-choke.auto-correct", true);
        plugin.getConfig().addDefault(base + ".anti-choke.min-pitch", 15.0D);
        plugin.getConfig().addDefault(base + ".anti-choke.max-pitch", 70.0D);

        plugin.getConfig().addDefault(base + ".godbridge.enabled", true);
        plugin.getConfig().addDefault(base + ".godbridge.min-pitch", 75.0D);
        plugin.getConfig().addDefault(base + ".godbridge.max-pitch", 85.0D);
        plugin.getConfig().addDefault(base + ".godbridge.max-distance", 1.5D);

        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        visualizerThickness = clampFloat(
                (float) plugin.getConfig().getDouble(base + ".visualizer.thickness", 0.025D),
                0.001F,
                0.25F
        );

        String materialName = plugin.getConfig().getString(base + ".visualizer.material", "BLACK_CONCRETE");
        Material parsedMaterial = Material.matchMaterial(materialName == null ? "BLACK_CONCRETE" : materialName);
        if (parsedMaterial == null || !parsedMaterial.isBlock()) {
            plugin.getLogger().warning("Invalid visualizer material '" + materialName + "' for " + base + ".visualizer.material. Falling back to BLACK_CONCRETE.");
            parsedMaterial = Material.BLACK_CONCRETE;
        }
        visualizerMaterial = parsedMaterial;

        brightnessBlock = clampInt(plugin.getConfig().getInt(base + ".visualizer.brightness.block", 15), 0, 15);
        brightnessSky = clampInt(plugin.getConfig().getInt(base + ".visualizer.brightness.sky", 15), 0, 15);

        antiChokeEnabled = plugin.getConfig().getBoolean(base + ".anti-choke.enabled", true);
        antiChokeAutoCorrect = plugin.getConfig().getBoolean(base + ".anti-choke.auto-correct", true);
        antiChokeMinPitch = clampFloat((float) plugin.getConfig().getDouble(base + ".anti-choke.min-pitch", 15.0D), -90.0F, 90.0F);
        antiChokeMaxPitch = clampFloat((float) plugin.getConfig().getDouble(base + ".anti-choke.max-pitch", 70.0D), -90.0F, 90.0F);

        if (antiChokeMinPitch > antiChokeMaxPitch) {
            float tmp = antiChokeMinPitch;
            antiChokeMinPitch = antiChokeMaxPitch;
            antiChokeMaxPitch = tmp;
        }

        godbridgeEnabled = plugin.getConfig().getBoolean(base + ".godbridge.enabled", true);
        godbridgeMinPitch = clampFloat((float) plugin.getConfig().getDouble(base + ".godbridge.min-pitch", 75.0D), -90.0F, 90.0F);
        godbridgeMaxPitch = clampFloat((float) plugin.getConfig().getDouble(base + ".godbridge.max-pitch", 85.0D), -90.0F, 90.0F);
        godbridgeMaxDistance = Math.max(0.5D, plugin.getConfig().getDouble(base + ".godbridge.max-distance", 1.5D));
    }

    private void updateCachedValues() {
        cachedVisualizerBlockData = visualizerMaterial.createBlockData();
        cachedBrightness = new Display.Brightness(brightnessBlock, brightnessSky);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand == null || hand != EquipmentSlot.HAND) {
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

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isPlaceableBlock(mainHand)) {
            return;
        }

        double reach = getReach(player);

        if (action == Action.RIGHT_CLICK_AIR) {
            VisualizerOutline outline = visualizers.get(player.getUniqueId());
            if (outline != null) {
                Block visualizerTarget = outline.getCurrentTarget();

                if (visualizerTarget != null) {
                    if (isReplaceable(visualizerTarget)
                            && !isPlayerIntersectingBlock(player, visualizerTarget)
                            && !isBlockedByEntity(visualizerTarget)) {

                        Block support = getSolidNeighbor(visualizerTarget);
                        if (support != null) {
                            boolean placed = placeBlockWithProtection(
                                    player,
                                    hand,
                                    mainHand,
                                    visualizerTarget,
                                    support
                            );

                            if (placed) {
                                event.setUseItemInHand(Event.Result.DENY);
                                event.setUseInteractedBlock(Event.Result.DENY);
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            BlockFace face = event.getBlockFace();

            if (clicked != null && face != null) {
                RayTraceResult trace = player.getWorld().rayTraceBlocks(
                        player.getEyeLocation(),
                        player.getEyeLocation().getDirection(),
                        reach,
                        FluidCollisionMode.NEVER,
                        true
                );

                if (trace != null && trace.getHitBlock() != null && trace.getHitBlock().equals(clicked)) {
                    Block expectedNormalPlacement = clicked.getRelative(face);
                    VisualizerOutline outline = visualizers.get(player.getUniqueId());
                    Block visualizerTarget = outline != null ? outline.getCurrentTarget() : null;

                    if (visualizerTarget == null || !visualizerTarget.equals(expectedNormalPlacement)) {
                        return;
                    }
                }
            }
        }

        Optional<BridgingTarget> bridgingTarget = getBedrockBridgingTarget(player, reach, true);

        if (bridgingTarget.isPresent()) {
            BridgingTarget target = bridgingTarget.get();

            if (!isReplaceable(target.targetBlock)) {
                return;
            }

            if (isPlayerIntersectingBlock(player, target.targetBlock)) {
                return;
            }

            boolean placed = placeBlockWithProtection(player, hand, mainHand, target.targetBlock, target.supportBlock);
            if (placed) {
                event.setUseItemInHand(Event.Result.DENY);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setCancelled(true);
            }
            return;
        }

        handleGodbridgeFallback(event, player, hand, mainHand);
        handleAntiChokeFallback(event, player, hand, mainHand);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof BlockDisplay)) {
            return;
        }

        Player player = event.getPlayer();

        if (!ModuleAccess.isEnabledForPlayer(plugin, this, player)) {
            return;
        }

        VisualizerOutline outline = visualizers.get(player.getUniqueId());
        if (outline == null || !outline.owns(event.getRightClicked())) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        ItemStack item = player.getInventory().getItem(hand);

        if (!isPlaceableBlock(item)) {
            return;
        }

        Block target = outline.getCurrentTarget();
        if (target == null || !isReplaceable(target)) {
            return;
        }

        Block support = getSolidNeighbor(target);
        if (support != null && placeBlockWithProtection(player, hand, item, target, support)) {
            event.setCancelled(true);
        }
    }

    private void handleGodbridgeFallback(PlayerInteractEvent event, Player player, EquipmentSlot hand, ItemStack item) {
        if (!godbridgeEnabled) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (player.isFlying() || player.isGliding()) {
            return;
        }

        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();

        if (clicked == null || face == null) {
            return;
        }

        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            return;
        }

        float pitch = player.getEyeLocation().getPitch();
        if (pitch < godbridgeMinPitch || pitch > godbridgeMaxPitch) {
            return;
        }

        Location playerLoc = player.getLocation();
        int playerY = playerLoc.getBlockY();

        if (clicked.getY() > playerY) {
            return;
        }

        Vector velocity = player.getVelocity();
        Vector direction = player.getEyeLocation().getDirection();
        direction.setY(0).normalize();
        velocity.setY(0);

        double velocityMagnitude = velocity.length();
        if (velocityMagnitude < 0.05D) {
            return;
        }

        double dotProduct = direction.dot(velocity);
        if (dotProduct > -0.05D) {
            return;
        }

        int targetY = playerY - 1;
        Block targetBlock = null;

        Vector backwardDir = direction.clone().multiply(-1);
        Location targetLoc = playerLoc.clone().add(backwardDir.multiply(0.3D));
        targetBlock = targetLoc.getWorld().getBlockAt(
            (int) Math.floor(targetLoc.getX()),
            targetY,
            (int) Math.floor(targetLoc.getZ())
        );

        if (targetBlock == null || !isReplaceable(targetBlock)) {
            return;
        }

        if (isPlayerIntersectingBlock(player, targetBlock)) {
            return;
        }

        if (isBlockedByEntity(targetBlock)) {
            return;
        }

        placeBlockWithProtection(player, hand, item, targetBlock, clicked);
    }

    private void handleAntiChokeFallback(PlayerInteractEvent event, Player player, EquipmentSlot hand, ItemStack item) {
        if (!antiChokeEnabled) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (player.isFlying() || player.isGliding()) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getBlockFace() != BlockFace.UP) {
            return;
        }

        Block aboveClicked = clicked.getRelative(BlockFace.UP);
        int playerY = player.getLocation().getBlockY();
        if (aboveClicked.getY() != playerY) {
            return;
        }

        Location playerLoc = player.getLocation();
        double playerX = playerLoc.getX();
        double playerZ = playerLoc.getZ();
        double clickedCenterX = clicked.getX() + 0.5D;
        double clickedCenterZ = clicked.getZ() + 0.5D;

        double horizontalDist = Math.sqrt(
            Math.pow(playerX - clickedCenterX, 2) +
            Math.pow(playerZ - clickedCenterZ, 2)
        );

        if (horizontalDist > 1.2D) {
            return;
        }

        float pitch = player.getEyeLocation().getPitch();
        if (pitch < antiChokeMinPitch || pitch > antiChokeMaxPitch) {
            return;
        }

        Location playerLocation = player.getLocation();
        double dx = playerLocation.getX() - (aboveClicked.getX() + 0.5D);
        double dz = playerLocation.getZ() - (aboveClicked.getZ() + 0.5D);
        if ((dx * dx + dz * dz) >= 4.0D) {
            return;
        }

        Vector direction = player.getEyeLocation().getDirection();
        BlockFace horizontalFacing = getHorizontalFacing(direction);
        Block extensionBlock = clicked.getRelative(horizontalFacing);

        double extCenterX = extensionBlock.getX() + 0.5D;
        double extCenterZ = extensionBlock.getZ() + 0.5D;
        double toExtX = extCenterX - playerX;
        double toExtZ = extCenterZ - playerZ;
        double dotProduct = (direction.getX() * toExtX) + (direction.getZ() * toExtZ);

        if (dotProduct <= 0.0D) {
            return;
        }

        if (!isReplaceable(extensionBlock)) {
            return;
        }

        if (extensionBlock.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return;
        }

        if (isPlayerIntersectingBlock(player, extensionBlock)) {
            return;
        }

        if (isBlockedByEntity(extensionBlock)) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);

        if (!antiChokeAutoCorrect) {
            return;
        }

        placeBlockWithProtection(player, hand, item, extensionBlock, clicked);
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

        Optional<BridgingTarget> target = getBedrockBridgingTarget(player, getReach(player), false);
        if (target.isEmpty()) {
            outline.hide();
            return;
        }

        outline.showAt(target.get().targetBlock);
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

        return isPlaceableBlock(player.getInventory().getItemInMainHand());
    }

    private Optional<BridgingTarget> getBedrockBridgingTarget(Player player, double maxDistance, boolean checkEntities) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        int playerY = playerLoc.getBlockY();

        float pitch = eye.getPitch();
        if (pitch < MIN_BRIDGING_PITCH) {
            return Optional.empty();
        }

        // GUARD CLAUSE: If the player looks directly at a block face, allow normal placement.
        RayTraceResult rayTrace = world.rayTraceBlocks(eye, direction, maxDistance, FluidCollisionMode.NEVER, true);
        if (rayTrace != null && rayTrace.getHitBlock() != null && rayTrace.getHitBlockFace() != null) {
            return Optional.empty();
        }

        // Proceed with bridging ONLY if hitting air/aiming past an edge.
        int targetY = playerY - 1;
        double topPlaneY = targetY + 1.0D;

        if (direction.getY() >= -0.001D) {
            return Optional.empty();
        }

        double t = (topPlaneY - eye.getY()) / direction.getY();
        if (t < 0.0D || t > maxDistance) {
            return Optional.empty();
        }

        double hitX = eye.getX() + (t * direction.getX());
        double hitZ = eye.getZ() + (t * direction.getZ());

        Block targetBlock = world.getBlockAt((int) Math.floor(hitX), targetY, (int) Math.floor(hitZ));
        if (isOutOfBounds(targetBlock, world)) {
            return Optional.empty();
        }

        if (targetBlock.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return Optional.empty();
        }

        if (!isReplaceable(targetBlock)) {
            return Optional.empty();
        }

        if (targetBlock.getY() >= playerY) {
            return Optional.empty();
        }

        if (isPlayerIntersectingBlock(player, targetBlock)) {
            return Optional.empty();
        }

        Block supportBlock = getSolidNeighbor(targetBlock);
        if (supportBlock == null) {
            return Optional.empty();
        }

        if (checkEntities && isBlockedByEntity(targetBlock)) {
            return Optional.empty();
        }

        return Optional.of(new BridgingTarget(targetBlock, supportBlock));
    }

    private Block getSolidNeighbor(Block block) {
        for (BlockFace face : NEIGHBOR_FACES) {
            Block neighbor = block.getRelative(face);
            if (!isReplaceable(neighbor)) {
                return neighbor;
            }
        }
        return null;
    }

    private boolean placeBlockWithProtection(Player player, EquipmentSlot hand, ItemStack sourceItem, Block targetBlock, Block supportBlock) {
        if (!isReplaceable(targetBlock)) {
            return false;
        }

        if (isOutOfBounds(targetBlock, targetBlock.getWorld())) {
            return false;
        }

        if (isPlayerIntersectingBlock(player, targetBlock)) {
            return false;
        }

        if (isBlockedByEntity(targetBlock)) {
            return false;
        }

        ItemStack currentHandItem = player.getInventory().getItem(hand);
        if (!isSameUsableBlockItem(currentHandItem, sourceItem)) {
            return false;
        }

        BlockState replacedState = targetBlock.getState();
        BlockData placedData;

        try {
            placedData = sourceItem.getType().createBlockData();
        } catch (IllegalArgumentException ex) {
            return false;
        }

        targetBlock.setBlockData(placedData, false);

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                targetBlock,
                replacedState,
                supportBlock,
                currentHandItem,
                player,
                true,
                hand
        );

        Bukkit.getPluginManager().callEvent(placeEvent);

        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            targetBlock.setBlockData(replacedState.getBlockData(), false);
            return false;
        }

        playPlacementSound(targetBlock.getWorld(), targetBlock);

        if (hand == EquipmentSlot.HAND) {
            player.swingMainHand();
        } else {
            player.swingOffHand();
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack liveHandItem = player.getInventory().getItem(hand);
            if (liveHandItem != null && liveHandItem.getType() == sourceItem.getType()) {
                int amount = liveHandItem.getAmount();
                if (amount <= 1) {
                    player.getInventory().setItem(hand, null);
                } else {
                    liveHandItem.setAmount(amount - 1);
                }
            }
        }

        return true;
    }

    private void playPlacementSound(World world, Block block) {
        try {
            Sound placeSound = block.getBlockData().getSoundGroup().getPlaceSound();
            world.playSound(block.getLocation().add(0.5D, 0.5D, 0.5D), placeSound, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } catch (Throwable ignored) {
            world.playSound(block.getLocation().add(0.5D, 0.5D, 0.5D), Sound.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    private boolean isPlaceableBlock(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Material type = stack.getType();
        return type.isBlock() && type.isSolid();
    }

    private boolean isSameUsableBlockItem(ItemStack current, ItemStack original) {
        if (current == null || original == null) {
            return false;
        }
        if (current.getType() != original.getType()) {
            return false;
        }
        return isPlaceableBlock(current);
    }

    private boolean isReplaceable(Block block) {
        return block.isEmpty() || block.isPassable();
    }

    private boolean isOutOfBounds(Block block, World world) {
        return block.getY() < world.getMinHeight() || block.getY() >= world.getMaxHeight();
    }

    private boolean isPlayerIntersectingBlock(Player player, Block targetBlock) {
        BoundingBox playerBox = player.getBoundingBox();
        BoundingBox blockBox = targetBlock.getBoundingBox();
        return playerBox.overlaps(blockBox.expand(-INTERSECTION_EPSILON));
    }

    private boolean isBlockedByEntity(Block targetBlock) {
        BoundingBox box = targetBlock.getBoundingBox();
        for (Entity entity : targetBlock.getWorld().getNearbyEntities(box)) {
            if (entity instanceof LivingEntity) {
                return true;
            }
        }
        return false;
    }

    private double getReach(Player player) {
        return player.getGameMode() == GameMode.CREATIVE ? CREATIVE_REACH : SURVIVAL_REACH;
    }

    private BlockFace getHorizontalFacing(Vector direction) {
        double x = direction.getX();
        double z = direction.getZ();

        if (Math.abs(x) > Math.abs(z)) {
            return x > 0.0D ? BlockFace.EAST : BlockFace.WEST;
        }
        return z > 0.0D ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class BridgingTarget {
        private final Block targetBlock;
        private final Block supportBlock;

        private BridgingTarget(Block targetBlock, Block supportBlock) {
            this.targetBlock = targetBlock;
            this.supportBlock = supportBlock;
        }
    }

    private final class VisualizerOutline {
        private final Player player;
        private final BlockDisplay[] edges = new BlockDisplay[12];
        private final Location baseLocation = new Location(null, 0.0D, 0.0D, 0.0D);
        private final Location teleportLocation = new Location(null, 0.0D, 0.0D, 0.0D);
        private Block currentTarget;

        private Block getCurrentTarget() {
            return currentTarget;
        }

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
                if (edge != null && !edge.isDead()) {
                    edge.remove();
                }
            }
            currentTarget = null;
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
