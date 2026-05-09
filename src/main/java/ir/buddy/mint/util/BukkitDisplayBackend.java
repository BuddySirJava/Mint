package ir.buddy.mint.util;

import ir.buddy.mint.MintPlugin;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;

import java.util.function.Consumer;

public final class BukkitDisplayBackend implements DisplayBackend {

    private final MintPlugin plugin;

    public BukkitDisplayBackend(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "bukkit";
    }

    @Override
    public boolean isProtocolLibBacked() {
        return false;
    }

    @Override
    public BlockDisplay spawnBlockDisplay(Location location, String source, Consumer<BlockDisplay> initializer) {
        if (!plugin.getDisplayEntityController().canSpawn(location, source)) {
            return null;
        }
        return location.getWorld().spawn(location, BlockDisplay.class, display -> {
            initializer.accept(display);
            plugin.getDisplayEntityController().markManaged(display, source);
        });
    }

    @Override
    public ItemDisplay spawnItemDisplay(Location location, String source, Consumer<ItemDisplay> initializer) {
        if (!plugin.getDisplayEntityController().canSpawn(location, source)) {
            return null;
        }
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            initializer.accept(display);
            plugin.getDisplayEntityController().markManaged(display, source);
        });
    }
}
