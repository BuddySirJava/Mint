package ir.buddy.mint.player;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.player.storage.PlayerToggleStorageFactory;
import ir.buddy.mint.player.storage.PlayerToggleStorage;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerModulePreferences {

    private final MintPlugin plugin;
    private PlayerToggleStorage storage;
    private final Map<UUID, Map<String, Boolean>> toggleCache = new HashMap<>();

    public PlayerModulePreferences(MintPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (storage != null) {
            storage.close();
        }

        this.storage = new PlayerToggleStorageFactory(plugin).createStorage();
        toggleCache.clear();
        plugin.getLogger().info("Player module toggle storage: " + storage.getDescription());
    }

    public void close() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
        toggleCache.clear();
    }

    public boolean isEnabledFor(Player player, Module module) {
        if (!module.isEnabledByConfig(plugin.getConfig())) {
            return false;
        }
        if (player == null) {
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        String moduleKey = getModuleKey(module);

        Map<String, Boolean> playerToggles = toggleCache.computeIfAbsent(playerUuid, ignored -> new HashMap<>());
        Boolean cachedValue = playerToggles.get(moduleKey);
        if (cachedValue != null) {
            return cachedValue;
        }

        boolean defaultForNew = module.defaultOnFirstJoin(plugin.getConfig());
        boolean loadedValue = storage.getToggle(playerUuid, moduleKey, defaultForNew);
        playerToggles.put(moduleKey, loadedValue);
        return loadedValue;
    }

    public boolean setEnabledFor(Player player, Module module, boolean enabled) {
        UUID playerUuid = player.getUniqueId();
        String moduleKey = getModuleKey(module);
        storage.setToggle(playerUuid, moduleKey, enabled);
        toggleCache.computeIfAbsent(playerUuid, ignored -> new HashMap<>()).put(moduleKey, enabled);
        return enabled;
    }

    public boolean toggleFor(Player player, Module module) {
        boolean next = !isEnabledFor(player, module);
        setEnabledFor(player, module, next);
        return next;
    }

    public void clearCachedToggles(UUID playerUuid) {
        toggleCache.remove(playerUuid);
    }

    public void preloadToggles(Collection<? extends Player> players, Collection<Module> modules) {
        if (players == null || players.isEmpty() || modules == null || modules.isEmpty()) {
            return;
        }

        Set<String> moduleKeys = new HashSet<>();
        for (Module module : modules) {
            moduleKeys.add(getModuleKey(module));
        }

        for (Player player : players) {
            UUID playerUuid = player.getUniqueId();
            Map<String, Boolean> loaded = storage.getToggles(playerUuid);
            Map<String, Boolean> cached = toggleCache.computeIfAbsent(playerUuid, ignored -> new HashMap<>());
            for (String moduleKey : moduleKeys) {
                cached.put(moduleKey, loaded.getOrDefault(moduleKey, defaultToggleForModuleKey(moduleKey)));
            }
        }
    }

    private String getModuleKey(Module module) {
        return module.getConfigPath().replaceFirst("^modules\\.", "");
    }

    private boolean defaultToggleForModuleKey(String moduleKey) {
        for (Module module : plugin.getModuleManager().getModules()) {
            if (getModuleKey(module).equals(moduleKey)) {
                return module.defaultOnFirstJoin(plugin.getConfig());
            }
        }
        return true;
    }
}
