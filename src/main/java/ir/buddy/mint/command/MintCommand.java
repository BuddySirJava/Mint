package ir.buddy.mint.command;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.MintLang;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class MintCommand implements CommandExecutor, TabCompleter {

    private final MintPlugin plugin;

    public MintCommand(MintPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MintLang lang = plugin.getMintLang();
        String guiPerm = plugin.getGuiConfig().getString("crafting-opener.permission", "mint.gui");

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                lang.send(sender, "errors.players-only", Map.of("label", label));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission(guiPerm)) {
                lang.send(sender, "errors.no-gui-permission", Map.of("label", label));
                return true;
            }
            plugin.getModuleToggleGui().openFor(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> lang.sendHelp(sender, label);
            case "about" -> lang.sendAbout(sender, label);
            case "admin" -> handleAdmin(sender, label, args, lang);
            default -> lang.send(sender, "errors.unknown-subcommand", Map.of("label", label));
        }
        return true;
    }

    private void handleAdmin(CommandSender sender, String label, String[] args, MintLang lang) {
        if (!sender.hasPermission("mint.admin")) {
            lang.send(sender, "errors.admin-no-access", Map.of("label", label));
            return;
        }
        if (args.length < 2) {
            lang.send(sender, "errors.admin-usage", Map.of("label", label));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload" -> handleReload(sender, label, lang);
            case "modules" -> sendModulesList(sender);
            case "toggle" -> handleToggle(sender, label, args, lang);
            case "global" -> handleGlobal(sender, label, args, lang);
            case "save" -> handleSave(sender, label, lang);
            default -> lang.send(sender, "errors.admin-usage", Map.of("label", label));
        }
    }

    private void handleReload(CommandSender sender, String label, MintLang lang) {
        if (!sender.hasPermission("mint.reload")) {
            lang.send(sender, "errors.no-permission", Map.of("label", label));
            return;
        }
        plugin.reloadPlugin();
        lang.send(sender, "admin.reload-done", Map.of("label", label));
    }

    private void handleSave(CommandSender sender, String label, MintLang lang) {
        if (!sender.hasPermission("mint.reload")) {
            lang.send(sender, "errors.no-permission", Map.of("label", label));
            return;
        }
        plugin.saveConfig();
        lang.send(sender, "admin.save-done", Map.of("label", label));
    }

    private void handleGlobal(CommandSender sender, String label, String[] args, MintLang lang) {
        if (!sender.hasPermission("mint.admin.global")) {
            lang.send(sender, "errors.no-permission", Map.of("label", label));
            return;
        }
        if (args.length < 4) {
            lang.send(sender, "errors.global-usage", Map.of("label", label));
            return;
        }

        Optional<Module> targetModule = plugin.getModuleManager().findModuleByInput(args[2]);
        if (targetModule.isEmpty()) {
            lang.send(sender, "errors.unknown-module", Map.of("label", label, "module", args[2]));
            return;
        }

        String stateArg = args[3].toLowerCase(Locale.ROOT);
        Boolean enabled;
        if (stateArg.equals("on") || stateArg.equals("true") || stateArg.equals("1")) {
            enabled = true;
        } else if (stateArg.equals("off") || stateArg.equals("false") || stateArg.equals("0")) {
            enabled = false;
        } else {
            lang.send(sender, "errors.global-bad-state", Map.of("label", label));
            return;
        }

        YamlConfiguration oldSnapshot = new YamlConfiguration();
        plugin.getConfig().getValues(true).forEach(oldSnapshot::set);

        Module module = targetModule.get();
        plugin.getConfig().set(module.getConfigPath() + ".enabled", enabled);
        plugin.saveConfig();
        plugin.getModuleManager().reloadChangedModules(oldSnapshot);
        if (plugin.getModuleToggleGui() != null) {
            plugin.getModuleToggleGui().reload();
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("label", label);
        ph.put("module", module.getName());
        ph.put("state", enabled ? lang.stateEnabledMini() : lang.stateDisabledMini());
        lang.send(sender, "admin.global-set", ph);
    }

    private void handleToggle(CommandSender sender, String label, String[] args, MintLang lang) {
        if (!sender.hasPermission("mint.toggle")) {
            lang.send(sender, "errors.no-permission", Map.of("label", label));
            return;
        }
        if (args.length < 3) {
            lang.send(sender, "errors.toggle-usage", Map.of("label", label));
            return;
        }

        Optional<Module> targetModule = plugin.getModuleManager().findModuleByInput(args[2]);
        if (targetModule.isEmpty()) {
            lang.send(sender, "errors.unknown-module", Map.of("label", label, "module", args[2]));
            return;
        }

        Player targetPlayer;
        if (args.length >= 4) {
            if (!sender.hasPermission("mint.toggle.others")) {
                lang.send(sender, "errors.no-permission", Map.of("label", label));
                return;
            }
            targetPlayer = plugin.getServer().getPlayerExact(args[3]);
            if (targetPlayer == null) {
                lang.send(sender, "errors.player-not-found", Map.of("label", label, "player", args[3]));
                return;
            }
        } else if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        } else {
            lang.send(sender, "errors.console-needs-player", Map.of("label", label));
            return;
        }

        Module module = targetModule.get();
        boolean enabledAfterToggle = plugin.getPlayerModulePreferences().toggleFor(targetPlayer, module);

        Map<String, String> ph = new HashMap<>();
        ph.put("label", label);
        ph.put("module", module.getName());
        ph.put("player", targetPlayer.getName());
        ph.put("state", enabledAfterToggle ? lang.stateEnabledMini() : lang.stateDisabledMini());
        lang.send(sender, "admin.toggle-done", ph);

        if (!module.isEnabledByConfig(plugin.getConfig())) {
            lang.send(sender, "admin.toggle-note-globally-off", Map.of("label", label));
        }
    }

    private void sendModulesList(CommandSender sender) {
        if (!(sender instanceof Audience)) {
            return;
        }
        Audience audience = (Audience) sender;
        audience.sendMessage(Component.text("Modules", NamedTextColor.GOLD));
        Player player = sender instanceof Player ? (Player) sender : null;
        for (Module module : plugin.getModuleManager().getModules()) {
            boolean enabled = module.isEnabledByConfig(plugin.getConfig());
            boolean enabledForPlayer = plugin.getPlayerModulePreferences().isEnabledFor(player, module);

            Component globalPart = Component.text(
                    enabled ? "enabled" : "disabled",
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED
            );

            Component line = Component.text("- ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(module.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(globalPart)
                    .append(Component.text(player != null ? ", you: " : "", NamedTextColor.GRAY));

            if (player != null) {
                Component youPart = Component.text(
                        enabledForPlayer ? "on" : "off",
                        enabledForPlayer ? NamedTextColor.GREEN : NamedTextColor.RED
                );
                line = line.append(youPart);
            }

            line = line.append(Component.text(") — ", NamedTextColor.GRAY))
                    .append(Component.text(
                            module.getDescription() == null ? "" : module.getDescription(),
                            NamedTextColor.GRAY
                    ));
            audience.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("help", "about", "admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("mint.admin")) {
            return filterPrefix(List.of("reload", "modules", "toggle", "global", "save"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("toggle") || args[1].equalsIgnoreCase("global")) {
                return filterPrefix(
                        plugin.getModuleManager().getModules().stream()
                                .map(plugin.getModuleManager()::getModuleKey)
                                .collect(Collectors.toList()),
                        args[2]
                );
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("toggle")
                && sender.hasPermission("mint.toggle.others")) {
            return filterPrefix(
                    plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()),
                    args[3]
            );
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("global")) {
            return filterPrefix(List.of("on", "off"), args[3]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}
