package ir.buddy.mint.util;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;

import java.util.function.Consumer;

public interface DisplayBackend {

    String name();

    boolean isProtocolLibBacked();

    BlockDisplay spawnBlockDisplay(Location location, String source, Consumer<BlockDisplay> initializer);

    ItemDisplay spawnItemDisplay(Location location, String source, Consumer<ItemDisplay> initializer);
}
