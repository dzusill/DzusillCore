package me.dzusill.core.message;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The {@code Presentation} block of a plugin's config: how each kind of message is delivered, and the per-key
 * exceptions.
 *
 * <p>
 * Resolution runs override, then category, then plain chat. That order is what keeps the common case to four lines of
 * config — say once that toggles go to the action bar and errors go to both — while still allowing the one message
 * somebody wants to treat differently.
 * </p>
 *
 * <p>
 * Immutable. Reloading builds a new one and swaps it in, so a message being sent can never see half a config.
 * </p>
 */
public final class PresentationSettings {

    /** Everything in chat, nothing audible: the behaviour of a plugin that ships no {@code Presentation} block. */
    public static final PresentationSettings DEFAULTS = new PresentationSettings(Map.of(), Map.of());

    private final Map<MessageCategory, Presentation> categories;
    private final Map<String, Presentation> overrides;

    private PresentationSettings(Map<MessageCategory, Presentation> categories, Map<String, Presentation> overrides) {
        // Seeded by class, not by copying the map: EnumMap cannot infer its key type from an empty one, and the empty
        // case is the common one — every plugin that ships no Presentation block builds exactly that.
        this.categories = new EnumMap<>(MessageCategory.class);
        this.categories.putAll(categories);
        this.overrides = Map.copyOf(overrides);
    }

    /**
     * Reads the block.
     *
     * @param root
     *            the {@code Presentation} section, or {@code null} when the config has none — which is not an error and
     *            yields {@link #DEFAULTS}
     */
    public static PresentationSettings from(ConfigurationSection root) {
        if (root == null) {
            return DEFAULTS;
        }
        Map<MessageCategory, Presentation> categories = new EnumMap<>(MessageCategory.class);
        ConfigurationSection categorySection = root.getConfigurationSection("Categories");
        if (categorySection != null) {
            for (String name : categorySection.getKeys(false)) {
                MessageCategory category = MessageCategory.fromString(name);
                Presentation presentation = read(categorySection.getConfigurationSection(name));
                if (category != null && presentation != null) {
                    categories.put(category, presentation);
                }
            }
        }
        Map<String, Presentation> overrides = new HashMap<>();
        ConfigurationSection overrideSection = root.getConfigurationSection("Overrides");
        if (overrideSection != null) {
            for (String key : overrideSection.getKeys(true)) {
                ConfigurationSection entry = overrideSection.getConfigurationSection(key);
                Presentation presentation = entry == null ? null : read(entry);
                if (presentation != null) {
                    overrides.put(key.toLowerCase(Locale.ROOT), presentation);
                }
            }
        }
        return new PresentationSettings(categories, overrides);
    }

    /**
     * One entry, with anything it does not state left {@code null} to inherit.
     *
     * @return {@code null} when the entry states nothing at all — a section holding only comments must not count as an
     *         override that silences the sound
     */
    private static Presentation read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        MessageChannel channel = section.isString("Channel")
                ? MessageChannel.fromString(section.getString("Channel"), MessageChannel.CHAT)
                : null;
        SoundSpec sound = readSound(section.getConfigurationSection("Sound"));
        return channel == null && sound == null ? null : new Presentation(channel, sound);
    }

    private static SoundSpec readSound(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        if (!section.getBoolean("Enabled", true)) {
            // Stated and switched off, which is different from unstated: it must not inherit a sound back.
            return SoundSpec.NONE;
        }
        String name = section.getString("Name", "");
        if (name.isBlank()) {
            return SoundSpec.NONE;
        }
        return new SoundSpec(name, (float) section.getDouble("Volume", 1.0D), (float) section.getDouble("Pitch", 1.0D));
    }

    /**
     * How to deliver one message.
     *
     * @param key
     *            its {@code messages.yml} key
     * @param category
     *            what kind of message it is, or {@code null} for uncategorised — which is {@link MessageCategory#INFO}
     */
    public Presentation resolve(String key, MessageCategory category) {
        Presentation base = categories.getOrDefault(category == null ? MessageCategory.INFO : category,
                Presentation.DEFAULT);
        Presentation override = key == null ? null : overrides.get(key.toLowerCase(Locale.ROOT));
        return override == null ? base.complete() : override.over(base).complete();
    }

    /** @return whether anything at all was configured, so a plugin can skip the work when nothing asked for it */
    public boolean isEmpty() {
        return categories.isEmpty() && overrides.isEmpty();
    }
}
