package ir.buddy.mint.player.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerToggleStorageSettingsTest {

    @Test
    void defaultsRepresentYamlWithFallbacks() {
        PlayerToggleStorageSettings defaults = PlayerToggleStorageSettings.defaults();
        assertEquals("yaml", defaults.normalizedType());
        assertEquals("jdbc:h2:file:./plugins/Mint/player-toggles;AUTO_SERVER=TRUE", defaults.url());
    }

    @Test
    void sqlTypeDetectionWorks() {
        PlayerToggleStorageSettings h2 = new PlayerToggleStorageSettings("h2", "h2", "", "", "", "", "", "");
        assertTrue(h2.isSqlType());
    }

    @Test
    void normalizeAliasAndNullType() {
        assertEquals("mongo", PlayerToggleStorageSettings.normalizeType("MANGO"));
        assertEquals("yaml", PlayerToggleStorageSettings.normalizeType(null));
    }
}
