package ir.buddy.mint.util;

import ir.buddy.mint.MintPlugin;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;

import java.util.function.Consumer;

public final class ProtocolLibDisplayBackend implements DisplayBackend {

    private final BukkitDisplayBackend fallback;

    public ProtocolLibDisplayBackend(MintPlugin plugin) {
        this.fallback = new BukkitDisplayBackend(plugin);
    }

    @Override
    public String name() {
        return "protocollib";
    }

    @Override
    public boolean isProtocolLibBacked() {
        return true;
    }

    @Override
    public BlockDisplay spawnBlockDisplay(Location location, String source, Consumer<BlockDisplay> initializer) {
        return fallback.spawnBlockDisplay(location, source, initializer);
    }

    @Override
    public ItemDisplay spawnItemDisplay(Location location, String source, Consumer<ItemDisplay> initializer) {
        return fallback.spawnItemDisplay(location, source, initializer);
    }
}
