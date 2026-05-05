package ir.buddy.mint.player.storage;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public interface PlayerToggleStorage {

    boolean getToggle(UUID playerUuid, String moduleKey, boolean defaultValue);

    default Map<String, Boolean> getToggles(UUID playerUuid) {
        return Collections.emptyMap();
    }

    void setToggle(UUID playerUuid, String moduleKey, boolean enabled);

    void close();

    String getDescription();
}
