package ir.buddy.mint.module;

public interface Module {

    String getName();

    String getConfigPath();

    String getDescription();

    void enable();

    void disable();


    default boolean supportsAsyncPreparation() {
        return false;
    }


    default void prepareEnable() throws Exception {

    }

    default boolean isEnabledByConfig(org.bukkit.configuration.file.FileConfiguration config) {
        return config.getBoolean(getConfigPath() + ".enabled", true);
    }

    /**
     * When a player has no stored preference yet, this value is used (if the module is enabled in config).
     * Missing key defaults to {@code true} so existing configs without the option keep prior behavior.
     */
    default boolean defaultOnFirstJoin(org.bukkit.configuration.file.FileConfiguration config) {
        return config.getBoolean(getConfigPath() + ".default-on-first-join", true);
    }
}
