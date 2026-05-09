package ir.buddy.mint.player.storage;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.util.FoliaScheduler;
import ir.buddy.mint.util.ScheduledTaskHandle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class YamlPlayerToggleStorage implements PlayerToggleStorage {

    private final MintPlugin plugin;
    private final File playerDataFolder;
    private final Map<UUID, Map<String, Boolean>> pendingWrites = new ConcurrentHashMap<>();
    private ScheduledTaskHandle saveTask;

    public YamlPlayerToggleStorage(MintPlugin plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "player-data");
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create player data folder: " + playerDataFolder.getAbsolutePath());
        }
        startAutoSave();
    }

    private void startAutoSave() {
        saveTask = FoliaScheduler.runGlobalAtFixedRate(plugin, 100L, 100L, () -> {
            if (pendingWrites.isEmpty()) {
                return;
            }
            Map<UUID, Map<String, Boolean>> snapshot = new HashMap<>();
            for (Map.Entry<UUID, Map<String, Boolean>> entry : pendingWrites.entrySet()) {
                snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            pendingWrites.clear();

            FoliaScheduler.runAsync(plugin, () -> {
                for (Map.Entry<UUID, Map<String, Boolean>> entry : snapshot.entrySet()) {
                    savePlayerToggles(entry.getKey(), entry.getValue());
                }
            });
        });
    }

    @Override
    public boolean getToggle(UUID playerUuid, String moduleKey, boolean defaultValue) {
        File playerFile = getPlayerFile(playerUuid);
        if (playerFile.exists()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(playerFile);
            return data.getBoolean(moduleKey, defaultValue);
        }

        return plugin.getConfig().getBoolean(getLegacyPath(playerUuid, moduleKey), defaultValue);
    }

    @Override
    public Map<String, Boolean> getToggles(UUID playerUuid) {
        File playerFile = getPlayerFile(playerUuid);
        if (playerFile.exists()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(playerFile);
            Map<String, Boolean> toggles = new HashMap<>();
            for (String moduleKey : data.getKeys(false)) {
                toggles.put(moduleKey, data.getBoolean(moduleKey, true));
            }
            return toggles;
        }

        String legacyPlayerPath = "player-module-toggles." + playerUuid;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(legacyPlayerPath);
        if (section == null) {
            return Map.of();
        }

        Map<String, Boolean> toggles = new HashMap<>();
        for (String moduleKey : section.getKeys(false)) {
            toggles.put(moduleKey, section.getBoolean(moduleKey, true));
        }
        return toggles;
    }

    @Override
    public void setToggle(UUID playerUuid, String moduleKey, boolean enabled) {
        pendingWrites.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>()).put(moduleKey, enabled);
    }

    @Override
    public void close() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (!pendingWrites.isEmpty()) {
            for (Map.Entry<UUID, Map<String, Boolean>> entry : pendingWrites.entrySet()) {
                savePlayerToggles(entry.getKey(), entry.getValue());
            }
            pendingWrites.clear();
        }
    }

    @Override
    public String getDescription() {
        return "yaml";
    }

    private File getPlayerFile(UUID uuid) {
        return new File(playerDataFolder, uuid + ".yml");
    }

    private String getLegacyPath(UUID uuid, String moduleKey) {
        return "player-module-toggles." + uuid + "." + moduleKey;
    }

    private void savePlayerToggles(UUID uuid, Map<String, Boolean> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        File playerFile = getPlayerFile(uuid);
        YamlConfiguration data = YamlConfiguration.loadConfiguration(playerFile);
        for (Map.Entry<String, Boolean> entry : updates.entrySet()) {
            data.set(entry.getKey(), entry.getValue());
        }

        try {
            data.save(playerFile);
        } catch (IOException ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Failed to write player toggle using yaml storage for " + uuid + ".",
                    ex
            );
        }
    }
}
