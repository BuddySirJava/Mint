package ir.buddy.mint.util;

import ir.buddy.mint.MintPlugin;
import org.bukkit.plugin.Plugin;

public final class DisplayBackendManager {

    private static final String CFG_BASE = "display-backend";
    private static final String MODE_AUTO = "auto";
    private static final String MODE_BUKKIT = "bukkit";
    private static final String MODE_PROTOCOLLIB = "protocollib";
    private static final String PROTOCOLLIB_PLUGIN_NAME = "ProtocolLib";
    private static final String PROTOCOLLIB_CORE_CLASS = "com.comphenix.protocol.ProtocolLibrary";

    private final MintPlugin plugin;
    private DisplayBackend backend;

    public DisplayBackendManager(MintPlugin plugin) {
        this.plugin = plugin;
    }

    public void initializeOrReload() {
        String configuredMode = plugin.getConfig().getString(CFG_BASE + ".mode", MODE_AUTO);
        String mode = configuredMode == null ? MODE_AUTO : configuredMode.trim().toLowerCase();
        boolean allowProtocolLib = plugin.getConfig().getBoolean(CFG_BASE + ".allow-protocollib", true);
        boolean logSelection = plugin.getConfig().getBoolean(CFG_BASE + ".log-selection", true);
        boolean foliaExperimentalEnabled = plugin.getConfig().getBoolean(CFG_BASE + ".folia-protocollib-experimental", false);
        boolean hasProtocolLib = isProtocolLibAvailable();
        boolean foliaBlocksProtocolLib = FoliaScheduler.isFolia() && !foliaExperimentalEnabled;
        String fallbackReason = null;

        if (MODE_PROTOCOLLIB.equals(mode)) {
            if (!allowProtocolLib) {
                backend = new BukkitDisplayBackend(plugin);
                fallbackReason = "allow-protocollib=false";
            } else if (!hasProtocolLib) {
                backend = new BukkitDisplayBackend(plugin);
                fallbackReason = "ProtocolLib is missing or disabled";
            } else if (foliaBlocksProtocolLib) {
                backend = new BukkitDisplayBackend(plugin);
                fallbackReason = "Folia experimental support is disabled (display-backend.folia-protocollib-experimental=false)";
            } else {
                backend = buildProtocolLibBackendOrFallback(mode);
                if (!backend.isProtocolLibBacked()) {
                    fallbackReason = "ProtocolLib backend initialization failed";
                }
            }
        } else if (MODE_BUKKIT.equals(mode)) {
            backend = new BukkitDisplayBackend(plugin);
        } else {
            if (!allowProtocolLib || !hasProtocolLib || foliaBlocksProtocolLib) {
                backend = new BukkitDisplayBackend(plugin);
                if (!allowProtocolLib) {
                    fallbackReason = "allow-protocollib=false";
                } else if (!hasProtocolLib) {
                    fallbackReason = "ProtocolLib is missing or disabled";
                } else {
                    fallbackReason = "Folia experimental support is disabled (display-backend.folia-protocollib-experimental=false)";
                }
            } else {
                backend = buildProtocolLibBackendOrFallback(mode);
                if (!backend.isProtocolLibBacked()) {
                    fallbackReason = "ProtocolLib backend initialization failed";
                }
            }
        }

        if (backend == null) {
            backend = new BukkitDisplayBackend(plugin);
        }
        if (fallbackReason != null && MODE_BUKKIT.equals(backend.name())) {
            plugin.getLogger().warning("Display backend fallback to bukkit: " + fallbackReason + ".");
        }
        if (logSelection) {
            plugin.getLogger().info("Display backend selected: " + backend.name() + " (mode=" + mode + ").");
        }
    }

    public DisplayBackend backend() {
        if (backend == null) {
            initializeOrReload();
        }
        return backend;
    }

    public boolean usingProtocolLibBackend() {
        return backend().isProtocolLibBacked();
    }

    private boolean isProtocolLibAvailable() {
        Plugin pluginEntry = plugin.getServer().getPluginManager().getPlugin(PROTOCOLLIB_PLUGIN_NAME);
        if (pluginEntry == null || !pluginEntry.isEnabled()) {
            return false;
        }
        return hasClass(PROTOCOLLIB_CORE_CLASS);
    }

    private boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private void logExperimentalFoliaWarning() {
        if (FoliaScheduler.isFolia()) {
            plugin.getLogger().warning("ProtocolLib backend on Folia is experimental. Mint will fall back to bukkit backend if ProtocolLib-backed initialization fails.");
        }
    }

    private DisplayBackend buildProtocolLibBackendOrFallback(String mode) {
        try {
            DisplayBackend selected = new ProtocolLibDisplayBackend(plugin);
            logExperimentalFoliaWarning();
            return selected;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to initialize ProtocolLib display backend in mode '" + mode
                    + "': " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage()
                    + ". Falling back to bukkit.");
            return new BukkitDisplayBackend(plugin);
        }
    }
}
