package ir.buddy.mint.player.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerToggleStorageFactoryTest {

    @Test
    void resolvesUnifiedSettingsForH2() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "h2");
        config.set("settings.url", "jdbc:h2:file:./custom");
        config.set("settings.username", "sa2");
        config.set("settings.password", "pw2");

        PlayerToggleStorageSettings settings = PlayerToggleStorageFactory.readSettings(config);

        assertEquals("h2", settings.normalizedType());
        assertEquals("jdbc:h2:file:./custom", settings.url());
        assertEquals("sa2", settings.username());
        assertEquals("pw2", settings.password());
    }

    @Test
    void supportsLegacyNestedFallback() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "mysql");
        config.set("mysql.url", "jdbc:mysql://db:3306/test");
        config.set("mysql.username", "legacy");
        config.set("mysql.password", "legacypw");

        PlayerToggleStorageSettings settings = PlayerToggleStorageFactory.readSettings(config);

        assertEquals("mysql", settings.normalizedType());
        assertEquals("jdbc:mysql://db:3306/test", settings.url());
        assertEquals("legacy", settings.username());
        assertEquals("legacypw", settings.password());
    }

    @Test
    void normalizesMangoAliasToMongo() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "mango");

        PlayerToggleStorageSettings settings = PlayerToggleStorageFactory.readSettings(config);

        assertEquals("mongo", settings.normalizedType());
    }

    @Test
    void trimsStorageTypeWhitespace() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("type", "  h2  ");

        PlayerToggleStorageSettings settings = PlayerToggleStorageFactory.readSettings(config);

        assertEquals("h2", settings.normalizedType());
    }
}
