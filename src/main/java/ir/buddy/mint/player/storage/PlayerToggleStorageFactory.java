package ir.buddy.mint.player.storage;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.util.JdbcDriverBinding;
import ir.buddy.mint.util.ShadedJdbcDrivers;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.logging.Level;

public class PlayerToggleStorageFactory {

    private final MintPlugin plugin;

    public PlayerToggleStorageFactory(MintPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerToggleStorage createStorage() {
        PlayerToggleStorageSettings settings = readSettings(
                plugin.getConfig().getConfigurationSection("storage.player-toggles")
        );

        ClassLoader pluginCl = plugin.getClass().getClassLoader();
        ClassLoader libCl = plugin.runtimeLibrariesClassLoaderOrNull();
        switch (settings.normalizedType()) {
            case "mariadb":
                return createJdbcStorage("mariadb", ShadedJdbcDrivers.mariadb(pluginCl, libCl), settings);
            case "mysql":
                return createJdbcStorage("mysql", ShadedJdbcDrivers.mysql(pluginCl, libCl), settings);
            case "h2":
                return createJdbcStorage("h2", ShadedJdbcDrivers.h2(pluginCl, libCl), settings);
            case "mongo":
                return createMongoStorage(settings);
            case "yaml":
            default:
                return new YamlPlayerToggleStorage(plugin);
        }
    }

    public static PlayerToggleStorageSettings readSettings(ConfigurationSection section) {
        PlayerToggleStorageSettings defaults = PlayerToggleStorageSettings.defaults();
        if (section == null) {
            return defaults;
        }

        String rawType = section.getString("type", defaults.rawType());
        String normalizedType = PlayerToggleStorageSettings.normalizeType(rawType);
        ConfigurationSection settings = section.getConfigurationSection("settings");

        String defaultUrl = defaults.url();
        String defaultUsername = defaults.username();
        if ("mariadb".equals(normalizedType)) {
            defaultUrl = "jdbc:mariadb://localhost:3306/mint";
            defaultUsername = "root";
        } else if ("mysql".equals(normalizedType)) {
            defaultUrl = "jdbc:mysql://localhost:3306/mint";
            defaultUsername = "root";
        } else if ("h2".equals(normalizedType)) {
            defaultUrl = "jdbc:h2:file:./plugins/Mint/player-toggles;AUTO_SERVER=TRUE";
            defaultUsername = "sa";
        }

        String url = getSettingWithLegacyFallback(settings, section, normalizedType, "url", defaultUrl);
        String username = getSettingWithLegacyFallback(settings, section, normalizedType, "username", defaultUsername);
        String password = getSettingWithLegacyFallback(settings, section, normalizedType, "password", defaults.password());

        String connectionString = getSettingWithLegacyFallback(
                settings,
                section,
                "mongo",
                "connection-string",
                defaults.connectionString()
        );
        String database = getSettingWithLegacyFallback(settings, section, "mongo", "database", defaults.database());
        String collection = getSettingWithLegacyFallback(settings, section, "mongo", "collection", defaults.collection());

        return new PlayerToggleStorageSettings(
                rawType == null ? defaults.rawType() : rawType,
                normalizedType,
                url,
                username,
                password,
                connectionString,
                database,
                collection
        );
    }

    private PlayerToggleStorage createJdbcStorage(String type, JdbcDriverBinding driver, PlayerToggleStorageSettings settings) {
        try {
            return new JdbcPlayerToggleStorage(
                    plugin,
                    type.toLowerCase(Locale.ROOT),
                    driver,
                    settings.url(),
                    settings.username(),
                    settings.password()
            );
        } catch (Throwable ex) {
            logStorageInitFailure(type, ex);
            return new YamlPlayerToggleStorage(plugin);
        }
    }

    private PlayerToggleStorage createMongoStorage(PlayerToggleStorageSettings settings) {
        try {
            ClassLoader libCl = plugin.runtimeLibrariesClassLoaderOrNull();
            if (libCl == null) {
                throw new IllegalStateException("plugins/Mint/lib/ is empty or missing Mongo jars");
            }
            return new ReflectiveMongoPlayerToggleStorage(
                    plugin,
                    libCl,
                    settings.connectionString(),
                    settings.database(),
                    settings.collection()
            );
        } catch (Throwable ex) {
            logStorageInitFailure("mongo", ex);
            return new YamlPlayerToggleStorage(plugin);
        }
    }

    private static String getSettingWithLegacyFallback(ConfigurationSection settings,
                                                       ConfigurationSection legacyRoot,
                                                       String legacyType,
                                                       String key,
                                                       String defaultValue) {
        if (settings != null) {
            String value = settings.getString(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        if (legacyRoot != null) {
            ConfigurationSection legacy = legacyRoot.getConfigurationSection(legacyType);
            if (legacy != null) {
                return legacy.getString(key, defaultValue);
            }
        }
        return defaultValue;
    }

    private void logStorageInitFailure(String type, Throwable ex) {
        plugin.getLogger().log(
                Level.WARNING,
                "Failed to initialize " + type + " storage, defaulting to yaml storage.",
                ex
        );
    }
}
