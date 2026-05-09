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
import java.util.stream.Collectors;

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

    


    public boolean isServerModuleActive(Module module) {
        return module.isServerScoped() && module.isEnabledByConfig(plugin.getConfig());
    }

    



    public boolean isPersonalModuleEnabled(Player player, Module module) {
        if (!module.isEnabledByConfig(plugin.getConfig())) {
            return false;
        }
        if (module.isServerScoped()) {
            return false;
        }
        if (player == null) {
            return true;
        }
        if (!isToggleEnabledFor(player, module)) {
            return false;
        }
        if (!hasModulePermission(player, module)) {
            return false;
        }
        if (!isAllowedInWorld(player, module)) {
            return false;
        }
        return true;
    }

    public boolean isToggleEnabledFor(Player player, Module module) {
        if (player == null) {
            return true;
        }
        if (module.isServerScoped()) {
            return false;
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
        if (module.isServerScoped()) {
            return enabled;
        }
        UUID playerUuid = player.getUniqueId();
        String moduleKey = getModuleKey(module);
        storage.setToggle(playerUuid, moduleKey, enabled);
        toggleCache.computeIfAbsent(playerUuid, ignored -> new HashMap<>()).put(moduleKey, enabled);
        return enabled;
    }

    public boolean toggleFor(Player player, Module module) {
        if (module.isServerScoped()) {
            return module.isEnabledByConfig(plugin.getConfig());
        }
        boolean next = !isPersonalModuleEnabled(player, module);
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
            if (module.isServerScoped()) {
                continue;
            }
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

    private boolean hasModulePermission(Player player, Module module) {
        String moduleKey = getModuleKey(module);
        String path = module.getConfigPath() + ".permission";
        String configured = plugin.getConfig().getString(path, "");
        String permission = (configured == null || configured.isBlank())
                ? "mint.module." + moduleKey
                : configured.trim();
        if (permission.equalsIgnoreCase("none") || permission.equalsIgnoreCase("disabled")) {
            return true;
        }
        return player.hasPermission(permission) || player.hasPermission("mint.module.*");
    }

    private boolean isAllowedInWorld(Player player, Module module) {
        String worldName = player.getWorld().getName();
        String basePath = module.getConfigPath() + ".worlds";
        String mode = plugin.getConfig().getString(basePath + ".mode", "all");
        Set<String> worlds = plugin.getConfig().getStringList(basePath + ".list").stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (worlds.isEmpty()) {
            worlds = plugin.getConfig().getStringList(module.getConfigPath() + ".enabled-worlds").stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toSet());
            if (!worlds.isEmpty()) {
                mode = "whitelist";
            }
        }
        if (worlds.isEmpty()) {
            worlds = plugin.getConfig().getStringList(module.getConfigPath() + ".disabled-worlds").stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toSet());
            if (!worlds.isEmpty()) {
                mode = "blacklist";
            }
        }

        String normalizedMode = mode == null ? "all" : mode.trim().toLowerCase();
        return switch (normalizedMode) {
            case "whitelist", "allow", "allowed" -> worlds.contains(worldName);
            case "blacklist", "deny", "denied", "block", "blocked" -> !worlds.contains(worldName);
            default -> true;
        };
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
