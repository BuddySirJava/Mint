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

    



    default boolean defaultOnFirstJoin(org.bukkit.configuration.file.FileConfiguration config) {
        return config.getBoolean(getConfigPath() + ".default-on-first-join", true);
    }

    



    default boolean isServerScoped() {
        return false;
    }
}
