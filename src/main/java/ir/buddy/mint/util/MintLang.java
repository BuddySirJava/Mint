package ir.buddy.mint.util;

import ir.buddy.mint.MintPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MintLang {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final MintPlugin plugin;

    public MintLang(MintPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String template = plugin.getLangConfig().getString(path);
        if (template == null || template.isBlank()) {
            return;
        }
        sendRaw(sender, parse(template, placeholders));
    }

    public void sendList(CommandSender sender, String listPath, Map<String, String> placeholders) {
        List<String> lines = plugin.getLangConfig().getStringList(listPath);
        if (lines.isEmpty()) {
            return;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            sendRaw(sender, parse(line, placeholders));
        }
    }

    public Component parse(String template, Map<String, String> placeholders) {
        TagResolver.Builder resolvers = TagResolver.builder();
        String prefixStr = plugin.getLangConfig().getString("prefix", "");
        Component prefixComp = prefixStr.isBlank()
                ? Component.empty()
                : MINI_MESSAGE.deserialize(prefixStr);
        resolvers.resolver(Placeholder.component("mint_prefix", prefixComp));

        Map<String, String> merged = new HashMap<>(placeholders);
        merged.putIfAbsent("version", pluginVersion());
        merged.putIfAbsent("author", pluginAuthor());
        for (Map.Entry<String, String> e : merged.entrySet()) {
            resolvers.resolver(Placeholder.unparsed(e.getKey(), e.getValue()));
        }
        return MINI_MESSAGE.deserialize(template, resolvers.build());
    }

    private void sendRaw(CommandSender sender, Component message) {
        if (sender instanceof Audience) {
            ((Audience) sender).sendMessage(message);
            return;
        }
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
    }

    public List<String> helpLinesForBookOrDebug() {
        return new ArrayList<>(plugin.getLangConfig().getStringList("help.lines"));
    }

    public String pluginVersion() {
        try {
            return plugin.getPluginMeta().getVersion();
        } catch (NoSuchMethodError ex) {
            return plugin.getDescription().getVersion();
        }
    }

    public String pluginAuthor() {
        try {
            List<String> authors = plugin.getPluginMeta().getAuthors();
            if (authors != null && !authors.isEmpty()) {
                return String.join(", ", authors);
            }
        } catch (NoSuchMethodError ignored) {
            // fall through
        }
        if (!plugin.getDescription().getAuthors().isEmpty()) {
            return String.join(", ", plugin.getDescription().getAuthors());
        }
        return "BuddySirJava";
    }

    public void sendAbout(CommandSender sender, String label) {
        Map<String, String> ph = new HashMap<>();
        ph.put("label", label);
        ph.put("version", pluginVersion());
        ph.put("author", pluginAuthor());
        sendList(sender, "about.lines", ph);
    }

    public void sendHelp(CommandSender sender, String label) {
        Map<String, String> ph = Map.of("label", label);
        sendRaw(sender, parse(plugin.getLangConfig().getString("help.header", ""), ph));
        sendList(sender, "help.lines", ph);
    }

    public String stateEnabledMini() {
        return plugin.getLangConfig().getString("messages.state-enabled", "enabled");
    }

    public String stateDisabledMini() {
        return plugin.getLangConfig().getString("messages.state-disabled", "disabled");
    }
}
