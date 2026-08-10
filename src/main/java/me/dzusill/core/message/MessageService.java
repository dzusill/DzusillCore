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
 * The build relocates {@code net.kyori} to {@code me.dzusill.core.lib.kyori}, so our {@link Component}/{@link Audience}
 * are DIFFERENT classes from the server's own Adventure: a bare {@code recipient instanceof Audience} check never
 * matches, not even on Paper. Delivery therefore walks three paths in order of fidelity:
 * </p>
 * <ol>
 * <li>{@link NativeAdventure} — hands the component to the server's own Adventure over JSON. Preferred on Paper and
 * every fork of it: full click/hover/hex fidelity, and it only uses Adventure's stable public API, so it does not break
 * when a new Minecraft version ships.</li>
 * <li>{@link BukkitAudiences} (adventure-platform-bukkit) — for plain Spigot, which has no Adventure of its own. This
 * one reflects into CraftBukkit internals and so needs a new release for each Minecraft version.</li>
 * <li>a legacy section-sign string — last resort, and a lossy one: it cannot carry click or hover events at all and
 * downsamples hex to the 16 named colors. Reaching it is logged once, loudly, because the symptom otherwise looks like
 * a plugin bug ("the message shows but clicking does nothing") with nothing in the log to explain it.</li>
 * </ol>
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
     * Bridges our relocated Adventure components on plain Spigot. Null when the platform can't initialize — which is
     * expected under MockBukkit, and harmless on Paper where {@link NativeAdventure} handles delivery instead.
     */
    private BukkitAudiences audiences;

    /** Guards the legacy-fallback warning so a busy server logs it once, not once per message. */
    private boolean warnedAboutLegacyFallback;

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
            } catch (Throwable failure) {
                // Never silent: on a server too new for the bundled adventure-platform this used to leave every
                // click and hover in every downstream plugin quietly broken, with nothing in the log to explain it.
                this.audiences = null;
                if (!NativeAdventure.available()) {
                    plugin.getLogger().warning("adventure-platform failed to start (" + failure
                            + ") and this server has no native Adventure — messages will lose click/hover events"
                            + " and hex colors. Update DzusillCore or run Paper.");
                }
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
        // 1. Paper and its forks: the server's own Adventure, reached over JSON. Full fidelity, and immune to the
        // Minecraft-version drift that breaks adventure-platform's CraftBukkit reflection.
        if (NativeAdventure.send(recipient, component)) {
            return;
        }
        // 2. Plain Spigot: the platform bridge.
        if (audiences != null) {
            audiences.sender(recipient).sendMessage(component);
            return;
        }
        // 3. Same relocation as ours (shaded into a consumer that didn't relocate) — rare, but free to support.
        if (recipient instanceof Audience audience) {
            audience.sendMessage(component);
            return;
        }
        // 4. Lossy last resort: legacy strings carry no click or hover events.
        warnLegacyFallbackOnce();
        recipient.sendMessage(LEGACY_SECTION.serialize(component));
    }

    private void warnLegacyFallbackOnce() {
        if (warnedAboutLegacyFallback) {
            return;
        }
        warnedAboutLegacyFallback = true;
        plugin.getLogger().warning("Falling back to legacy message delivery: click and hover events will not work and"
                + " hex colors are downsampled. Neither native Adventure nor adventure-platform is usable here.");
        Throwable why = NativeAdventure.lastFailure() != null
                ? NativeAdventure.lastFailure()
                : NativeAdventure.setupFailure();
        if (why != null) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "  native Adventure was unusable because:", why);
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
