package ir.buddy.mint.util;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import ir.buddy.mint.MintPlugin;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DisplayEntityController implements Listener {

    private static final String CFG_BASE = "display-entity-control";

    private final MintPlugin plugin;
    private final NamespacedKey managedKey;
    private final NamespacedKey sourceKey;
    private final Set<UUID> trackedLoadedDisplays = ConcurrentHashMap.newKeySet();
    private final Map<ChunkKey, AtomicInteger> managedCountByChunk = new ConcurrentHashMap<>();

    public DisplayEntityController(MintPlugin plugin) {
        this.plugin = plugin;
        this.managedKey = new NamespacedKey(plugin, "managed_display");
        this.sourceKey = new NamespacedKey(plugin, "managed_display_source");
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (isEnabled() && startupPruneLoadedChunks()) {
            FoliaScheduler.runGlobal(plugin, this::pruneLoadedChunks);
        }
    }

    public void reload() {
        trackedLoadedDisplays.clear();
        managedCountByChunk.clear();
        if (isEnabled() && startupPruneLoadedChunks()) {
            FoliaScheduler.runGlobal(plugin, this::pruneLoadedChunks);
        }
    }

    public boolean canSpawn(Location location, String source) {
        if (!isEnabled() || location == null || location.getWorld() == null) {
            return true;
        }
        Chunk chunk = location.getChunk();
        int perChunkHardCap = perChunkHardCap();
        if (perChunkHardCap > 0 && managedDisplaysInChunk(chunk) >= perChunkHardCap) {
            return false;
        }

        return true;
    }

    public void markManaged(Display display, String source) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(managedKey, PersistentDataType.BYTE, (byte) 1);
        if (source != null && !source.isBlank()) {
            pdc.set(sourceKey, PersistentDataType.STRING, source);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled() || !pruneOnChunkLoad()) {
            return;
        }
        Chunk chunk = event.getChunk();
        refreshChunkCount(chunk);
        pruneChunkIfNeeded(chunk, "chunk-load");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisplayAdded(EntityAddToWorldEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Display display)) {
            return;
        }
        if (!isManagedDisplay(display)) {
            return;
        }
        UUID id = display.getUniqueId();
        if (!trackedLoadedDisplays.add(id)) {
            return;
        }
        ChunkKey key = ChunkKey.of(display.getLocation().getChunk());
        managedCountByChunk.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisplayRemoved(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof Display display)) {
            return;
        }
        if (!isManagedDisplay(display)) {
            return;
        }
        UUID id = display.getUniqueId();
        if (!trackedLoadedDisplays.remove(id)) {
            return;
        }
        ChunkKey key = ChunkKey.of(display.getLocation().getChunk());
        decrementChunkCount(key);
    }

    private void pruneLoadedChunks() {
        for (var world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                refreshChunkCount(chunk);
                pruneChunkIfNeeded(chunk, "startup");
            }
        }
    }

    private void pruneChunkIfNeeded(Chunk chunk, String reason) {
        int chunkCap = perChunkHardCap();
        if (chunkCap <= 0) {
            return;
        }

        ChunkKey chunkKey = ChunkKey.of(chunk);
        int knownCount = managedDisplaysInChunk(chunk);
        if (knownCount <= chunkCap) {
            return;
        }

        List<Display> managed = collectManagedDisplays(chunk);
        managedCountByChunk.put(chunkKey, new AtomicInteger(managed.size()));
        if (managed.size() <= chunkCap) {
            return;
        }

        managed.sort(Comparator.comparing(entity -> entity.getUniqueId().toString()));
        int toRemove = managed.size() - chunkCap;
        for (int i = 0; i < toRemove; i++) {
            Display display = managed.get(i);
            UUID id = display.getUniqueId();
            if (trackedLoadedDisplays.remove(id)) {
                decrementChunkCount(chunkKey);
            }
            display.remove();
        }

        if (logPrunes()) {
            plugin.getLogger().warning(
                    "Pruned " + toRemove + " managed displays in chunk "
                            + chunk.getX() + "," + chunk.getZ()
                            + " (" + reason + ", cap=" + chunkCap + ")."
            );
        }
    }

    private int scanManagedDisplaysInChunk(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Display display && isManagedDisplay(display)) {
                count++;
            }
        }
        return count;
    }

    private int managedDisplaysInChunk(Chunk chunk) {
        ChunkKey key = ChunkKey.of(chunk);
        AtomicInteger cached = managedCountByChunk.get(key);
        if (cached != null) {
            return cached.get();
        }
        int scanned = scanManagedDisplaysInChunk(chunk);
        managedCountByChunk.put(key, new AtomicInteger(scanned));
        return scanned;
    }

    private void refreshChunkCount(Chunk chunk) {
        ChunkKey key = ChunkKey.of(chunk);
        managedCountByChunk.put(key, new AtomicInteger(scanManagedDisplaysInChunk(chunk)));
    }

    private void decrementChunkCount(ChunkKey key) {
        AtomicInteger count = managedCountByChunk.get(key);
        if (count == null) {
            return;
        }
        int updated = count.decrementAndGet();
        if (updated <= 0) {
            managedCountByChunk.remove(key, count);
        }
    }

    private List<Display> collectManagedDisplays(Chunk chunk) {
        List<Display> out = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Display display && isManagedDisplay(display)) {
                out.add(display);
            }
        }
        return out;
    }

    private boolean isManagedDisplay(Display display) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        if (pdc.has(managedKey, PersistentDataType.BYTE)) {
            return true;
        }
        for (NamespacedKey key : pdc.getKeys()) {
            String value = key.getKey();
            if ("decoration".equals(value)
                    || "carpet_geom".equals(value)
                    || "sign_item_marker".equals(value)
                    || "dyeable_item_frame_display".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnabled() {
        if (!plugin.getConfig().getBoolean(CFG_BASE + ".enabled", true)) {
            return false;
        }
        DisplayBackendManager manager = plugin.getDisplayBackendManager();
        return manager == null || !manager.usingProtocolLibBackend();
    }

    private int perChunkHardCap() {
        return Math.max(0, plugin.getConfig().getInt(CFG_BASE + ".per-chunk-hard-cap", 96));
    }

    private boolean startupPruneLoadedChunks() {
        return plugin.getConfig().getBoolean(CFG_BASE + ".startup-prune-loaded-chunks", true);
    }

    private boolean pruneOnChunkLoad() {
        return plugin.getConfig().getBoolean(CFG_BASE + ".prune-on-chunk-load", true);
    }

    private boolean logPrunes() {
        return plugin.getConfig().getBoolean(CFG_BASE + ".log-prunes", true);
    }

    private record ChunkKey(UUID worldId, int x, int z) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }
}
