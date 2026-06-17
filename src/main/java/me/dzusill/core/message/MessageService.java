package me.dzusill.core.message;

import me.dzusill.core.config.Config;
import me.dzusill.core.service.Reloadable;
import me.dzusill.core.service.Service;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves and sends user-facing messages defined in {@code messages.yml}, parsed with
 * Adventure's MiniMessage. Centralizes prefix handling, placeholder substitution and the
 * single/list distinction so call sites never touch raw color codes or component building.
 *
 * <p>{@code CommandSender#sendMessage(Component)} is a Paper-only overload, so it's never called
 * directly here. On Paper, the live sender object implements Adventure's {@link Audience} (even
 * though our compile-time {@code CommandSender} type, from Spigot API, doesn't expose that), so
 * the {@code instanceof} check below is true and gets native Component rendering. On plain
 * Spigot/CraftBukkit it's false, and we fall back to a legacy section-sign string, which every
 * Bukkit implementation has supported since {@code sendMessage(String)} existed.</p>
 */
public final class MessageService implements Service, Reloadable {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final String PREFIX_KEY = "prefix";
    private static final String PREFIX_TOKEN = "<prefix>";

    private final Plugin plugin;
    private Config config;
    private String prefix;

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        this.config = Config.loadConfig(plugin, "messages.yml", "messages.yml");
        this.prefix = config.getString(PREFIX_KEY, "");
    }

    /**
     * Builds a component for the given message key. Missing keys fall back to the key itself so
     * problems are visible in-game rather than silently swallowed.
     */
    public Component get(String key, Placeholder placeholder) {
        String raw = config.getString(key, key);
        return render(raw, placeholder);
    }

    public Component get(String key) {
        return get(key, Placeholder.empty());
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
     * Sends an already-built component, cross-version safe. The one sanctioned way to send a raw
     * {@link Component} outside this service — call sites should never call
     * {@code CommandSender#sendMessage(Component)} directly, since that overload is Paper-only
     * and won't even compile against plain Spigot's API.
     */
    public void sendComponent(CommandSender recipient, Component component) {
        if (recipient instanceof Audience audience) {
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
