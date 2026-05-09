package ir.buddy.mint.config;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.MintVersion;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.module.ModuleManager;
import ir.buddy.mint.player.storage.PlayerToggleStorageFactory;
import ir.buddy.mint.player.storage.PlayerToggleStorageSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;

public final class PluginConfigValidator {

    private PluginConfigValidator() {
    }

    public static void validateAndLog(MintPlugin plugin, ModuleManager moduleManager) {
        logConfigFileVersions(plugin);
        validateModuleKeys(plugin, moduleManager);
        validateStorageSettings(plugin);
    }

    


    private static void logConfigFileVersions(MintPlugin plugin) {
        String jar = MintVersion.plugin(plugin);
        String cfg = plugin.getConfig().getString("config-version");
        String gui = plugin.getGuiConfig() != null ? plugin.getGuiConfig().getString("gui-version") : null;
        String lang = plugin.getLangConfig() != null ? plugin.getLangConfig().getString("lang-version") : null;
        if (cfg != null && !cfg.equals(jar)) {
            plugin.getLogger().info("config.yml config-version is " + cfg + " (running Mint " + jar + "); defaults merged when outdated.");
        }
        if (gui != null && !gui.equals(jar)) {
            plugin.getLogger().info("gui.yml gui-version is " + gui + " (running Mint " + jar + "); defaults merged when outdated.");
        }
        if (lang != null && !lang.equals(jar)) {
            plugin.getLogger().info("lang.yml lang-version is " + lang + " (running Mint " + jar + "); defaults merged when outdated.");
        }
    }

    private static void validateModuleKeys(MintPlugin plugin, ModuleManager moduleManager) {
        ConfigurationSection modulesSection = plugin.getConfig().getConfigurationSection("modules");
        if (modulesSection == null) {
            plugin.getLogger().warning("Missing 'modules' section in config.yml.");
            return;
        }

        Set<String> configuredKeys = modulesSection.getKeys(false);
        Set<String> registeredKeys = new HashSet<>();
        for (Module module : moduleManager.getModules()) {
            registeredKeys.add(moduleManager.getModuleKey(module));
        }

        for (String configured : configuredKeys) {
            if (!registeredKeys.contains(configured)) {
                plugin.getLogger().warning("Unknown module config key: modules." + configured);
            }
        }

        for (String registered : registeredKeys) {
            if (!configuredKeys.contains(registered)) {
                plugin.getLogger().warning("Missing config section for registered module: modules." + registered);
            }
        }
    }

    private static void validateStorageSettings(MintPlugin plugin) {
        PlayerToggleStorageSettings settings = PlayerToggleStorageFactory.readSettings(
                plugin.getConfig().getConfigurationSection("storage.player-toggles")
        );

        String type = settings.normalizedType();
        if (!"yaml".equals(type) && !"h2".equals(type) && !"mysql".equals(type) && !"mariadb".equals(type) && !"mongo".equals(type)) {
            plugin.getLogger().warning(
                    "Unknown storage.player-toggles.type '" + settings.rawType() + "'. Falling back to yaml at runtime."
            );
            return;
        }

        if (settings.isSqlType()) {
            if (isBlank(settings.url())) {
                plugin.getLogger().warning("Missing storage.player-toggles.settings.url for SQL storage.");
            }
            if (isBlank(settings.username())) {
                plugin.getLogger().warning("Missing storage.player-toggles.settings.username for SQL storage.");
            }
            return;
        }

        if ("mongo".equals(type)) {
            if (isBlank(settings.connectionString())) {
                plugin.getLogger().warning("Missing storage.player-toggles.settings.connection-string for mongo storage.");
            }
            if (isBlank(settings.database())) {
                plugin.getLogger().warning("Missing storage.player-toggles.settings.database for mongo storage.");
            }
            if (isBlank(settings.collection())) {
                plugin.getLogger().warning("Missing storage.player-toggles.settings.collection for mongo storage.");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
