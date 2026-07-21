package me.dzusill.core.message;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import me.dzusill.core.config.Config;
import me.dzusill.core.service.Reloadable;
import me.dzusill.core.service.Service;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Resolves and sends user-facing messages defined in {@code messages.yml}, parsed with Adventure's MiniMessage.
 * Centralizes prefix handling, placeholder substitution and the single/list distinction so call sites never touch raw
 * color codes or component building.
 *
 * <p>
 * Sending goes through {@link BukkitAudiences} (adventure-platform-bukkit). This matters because the build relocates
 * {@code net.kyori} to {@code me.dzusill.core.lib.kyori}: our {@link Component}/{@link Audience} are therefore
 * DIFFERENT classes from the server's own Adventure, so a bare {@code recipient instanceof Audience} check would never
 * match — not even on Paper — and every message would silently degrade to a legacy section-sign string, dropping
 * click/hover events and hex colors. The platform bridges the relocated components to the client on both Spigot and
 * Paper. If the platform can't initialize (e.g. a non-server test harness), we fall back to the legacy string path,
 * which every Bukkit implementation has always supported.
 * </p>
 */
public final class MessageService implements Service, Reloadable {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final String PREFIX_KEY = "prefix";
    private static final String PREFIX_TOKEN = "<prefix>";

    private final Plugin plugin;
    private Config config;
    private String prefix;
    /**
     * Bridges our relocated Adventure components to the server (Spigot + Paper), preserving click/hover events and hex
     * colors. Null only if the platform can't initialize (e.g. a non-server test harness), in which case
     * {@link #sendComponent} degrades to the legacy string path.
     */
    private BukkitAudiences audiences;

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
        load();
        // adventure-platform doesn't route through MockBukkit's message log (breaking message-capture
        // tests), so skip it under the mock server and let sendComponent use the legacy path there.
        boolean mockServer = plugin.getServer().getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT)
                .contains("mock");
        if (!mockServer) {
            try {
                this.audiences = BukkitAudiences.create(plugin);
            } catch (Throwable ignored) {
                // Unusual platform: fall back to legacy string sending.
                this.audiences = null;
            }
        }
    }

    private void load() {
        this.config = Config.loadConfig(plugin, "messages.yml", "messages.yml");
        this.prefix = config.getString(PREFIX_KEY, "");
    }

    /**
     * Builds a component for the given message key. Missing keys fall back to the key itself so problems are visible
     * in-game rather than silently swallowed.
     */
    public Component get(String key, Placeholder placeholder) {
        String raw = config.getString(key, key);
        return render(raw, placeholder);
    }

    public Component get(String key) {
        return get(key, Placeholder.empty());
    }

    /**
     * The configured raw string for {@code key} (not rendered, {@code <prefix>} not expanded), or the key itself when
     * absent. Use this when message text is needed as a plain {@link String} — typically a value substituted into
     * another message's {@code %placeholder%} — rather than a rendered {@link Component}. Keep such values tag-free,
     * since they are parsed when the outer message renders.
     */
    public String raw(String key) {
        return config.getString(key, key);
    }

    /**
     * Builds components for a list-valued message key (e.g. multi-line usage text).
     */
    public List<Component> getList(String key, Placeholder placeholder) {
        List<String> raw = config.getStringList(key);
        List<Component> components = new ArrayList<>(raw.size());
        for (String line : raw) {
            components.add(render(line, placeholder));
        }
        return components;
    }

    /**
     * Sends the message at {@code key} to the recipient, applying placeholders.
     */
    public void send(CommandSender recipient, String key, Placeholder placeholder) {
        if (config.isList(key)) {
            getList(key, placeholder).forEach(line -> sendComponent(recipient, line));
        } else {
            sendComponent(recipient, get(key, placeholder));
        }
    }

    public void send(CommandSender recipient, String key) {
        send(recipient, key, Placeholder.empty());
    }

    /**
     * Parses and sends an ad-hoc MiniMessage string (not backed by a config key).
     */
    public void sendRaw(CommandSender recipient, String miniMessage, Placeholder placeholder) {
        sendComponent(recipient, render(miniMessage, placeholder));
    }

    /**
     * Sends an already-built component, cross-version safe. The one sanctioned way to send a raw {@link Component}
     * outside this service — call sites should never call {@code CommandSender#sendMessage(Component)} directly, since
     * that overload is Paper-only and won't even compile against plain Spigot's API.
     */
    public void sendComponent(CommandSender recipient, Component component) {
        if (audiences != null) {
            // Platform bridge: works on Spigot + Paper and keeps click/hover events and hex colors,
            // which a relocated-Adventure instanceof-Audience check can't (different class → always false).
            audiences.sender(recipient).sendMessage(component);
        } else if (recipient instanceof Audience audience) {
            audience.sendMessage(component);
        } else {
            recipient.sendMessage(LEGACY_SECTION.serialize(component));
        }
    }

    /**
     * Parses an ad-hoc MiniMessage string into a component.
     */
    public Component component(String miniMessage, Placeholder placeholder) {
        return render(miniMessage, placeholder);
    }

    private Component render(String raw, Placeholder placeholder) {
        String withPrefix = raw == null ? "" : raw.replace(PREFIX_TOKEN, prefix);
        String substituted = placeholder == null ? withPrefix : placeholder.apply(withPrefix);
        return MINI.deserialize(substituted);
    }

    @Override
    public void reload() {
        load();
    }
}
