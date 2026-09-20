package me.dzusill.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import me.dzusill.core.message.MessageService;
import me.dzusill.core.service.Reloadable;
import me.dzusill.core.service.Service;

/**
 * Central registry for every YAML config the plugin owns. Lets the plugin load named raw configs and typed
 * {@link AbstractConfig} wrappers, then reload them all at once (for example from a {@code /core reload} command).
 */
public final class ConfigManager implements Service, Reloadable {

    private final Plugin plugin;
    private final Map<String, Config> rawConfigs = new HashMap<>();
    private final Map<Class<? extends AbstractConfig>, AbstractConfig> typedConfigs = new HashMap<>();
    /** Everything else that re-reads a file of its own — see {@link #registerReloadable}. */
    private final List<Reloadable> reloadables = new ArrayList<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or returns the cached) raw config backed by the given file name.
     *
     * @param fileName
     *            resource and on-disk file name (e.g. {@code "data.yml"})
     */
    public Config load(String fileName) {
        return rawConfigs.computeIfAbsent(fileName, name -> Config.loadConfig(plugin, name, name));
    }

    /**
     * @return a previously loaded raw config, or {@code null} if it was never loaded
     */
    public Config get(String fileName) {
        return rawConfigs.get(fileName);
    }

    /**
     * Registers a typed config wrapper so it participates in {@link #reload()}.
     *
     * @return the same instance, for fluent assignment
     */
    public <T extends AbstractConfig> T register(T config) {
        typedConfigs.put(config.getClass(), config);
        return config;
    }

    /**
     * @return a registered typed config by its class
     * @throws IllegalStateException
     *             if the config was not registered
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractConfig> T get(Class<T> type) {
        AbstractConfig config = typedConfigs.get(type);
        if (config == null) {
            throw new IllegalStateException("No config registered for type " + type.getName());
        }
        return (T) config;
    }

    /**
     * Registers anything else that re-reads a file, so one reload covers it too.
     *
     * <p>
     * {@link MessageService} is the reason this exists. It owns {@code messages.yml} through its own {@link Config}
     * rather than through this manager, and it is not an {@link AbstractConfig}, so {@link #register} cannot take it —
     * which meant a {@code /<plugin> reload} re-read every config the plugin had <em>except its messages</em>. Editing
     * a line of text needed a full server or PlugMan reload, silently, in every plugin built on this core.
     * </p>
     *
     * <p>
     * Registering the same instance twice is a no-op, so a plugin that also wires it by hand does not reload twice.
     * </p>
     *
     * @return the same instance, for fluent assignment
     */
    public <T extends Reloadable> T registerReloadable(T reloadable) {
        if (reloadable != null && !reloadables.contains(reloadable)) {
            reloadables.add(reloadable);
        }
        return reloadable;
    }

    /**
     * Reloads every registered raw config, typed config and {@link Reloadable} from disk.
     *
     * @throws IllegalStateException
     *             when a registered reloadable fails; the caller reports it rather than the plugin dying, and the cause
     *             names which one it was
     */
    @Override
    public void reload() {
        List<String> names = new ArrayList<>(rawConfigs.keySet());
        for (String name : names) {
            rawConfigs.put(name, rawConfigs.get(name).reload());
        }
        for (AbstractConfig config : typedConfigs.values()) {
            config.reload();
        }
        for (Reloadable reloadable : reloadables) {
            try {
                reloadable.reload();
            } catch (Exception failed) {
                throw new IllegalStateException("Reloading " + reloadable.getClass().getSimpleName() + " failed",
                        failed);
            }
        }
    }
}
