package ir.buddy.mint.player.storage;

import java.util.Locale;

public record PlayerToggleStorageSettings(
        String rawType,
        String normalizedType,
        String url,
        String username,
        String password,
        String connectionString,
        String database,
        String collection
) {

    public static PlayerToggleStorageSettings defaults() {
        return new PlayerToggleStorageSettings(
                "yaml",
                "yaml",
                "jdbc:h2:file:./plugins/Mint/player-toggles;AUTO_SERVER=TRUE",
                "sa",
                "",
                "mongodb://localhost:27017",
                "mint",
                "player_module_toggles"
        );
    }

    public boolean isSqlType() {
        return "h2".equals(normalizedType) || "mysql".equals(normalizedType) || "mariadb".equals(normalizedType);
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "yaml";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if ("mango".equals(normalized)) {
            return "mongo";
        }
        return normalized;
    }
}
