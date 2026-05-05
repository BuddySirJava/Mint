package ir.buddy.mint.integration;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class MintPlaceholders extends PlaceholderExpansion {

    private final MintPlugin plugin;

    public MintPlaceholders(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mint";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BuddySirJava";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer != null ? offlinePlayer.getPlayer() : null;

        if (params.equalsIgnoreCase("modules_total")) {
            return String.valueOf(plugin.getModuleManager().getModules().size());
        }

        if (params.equalsIgnoreCase("modules_enabled_count")) {
            long count = plugin.getModuleManager().getModules().stream()
                    .filter(module -> plugin.getPlayerModulePreferences().isEnabledFor(player, module))
                    .count();
            return String.valueOf(count);
        }

        if (params.toLowerCase().startsWith("module_")) {
            String query = params.substring("module_".length());
            Optional<Module> module = plugin.getModuleManager().findModuleByInput(query);
            if (module.isEmpty()) {
                return "unknown";
            }
            return plugin.getPlayerModulePreferences().isEnabledFor(player, module.get()) ? "enabled" : "disabled";
        }

        if (params.toLowerCase().startsWith("global_")) {
            String query = params.substring("global_".length());
            Optional<Module> module = plugin.getModuleManager().findModuleByInput(query);
            if (module.isEmpty()) {
                return "unknown";
            }
            return module.get().isEnabledByConfig(plugin.getConfig()) ? "enabled" : "disabled";
        }

        return null;
    }
}
