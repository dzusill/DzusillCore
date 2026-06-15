package me.dzusill.core.config;

import me.dzusill.core.service.Reloadable;
import me.dzusill.core.service.Service;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for every YAML config the plugin owns. Lets the plugin load named raw configs
 * and typed {@link AbstractConfig} wrappers, then reload them all at once (for example from a
 * {@code /core reload} command).
 */
public final class ConfigManager implements Service, Reloadable {

    private final Plugin plugin;
    private final Map<String, Config> rawConfigs = new HashMap<>();
    private final Map<Class<? extends AbstractConfig>, AbstractConfig> typedConfigs = new HashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or returns the cached) raw config backed by the given file name.
     *
     * @param fileName resource and on-disk file name (e.g. {@code "data.yml"})
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
     * @throws IllegalStateException if the config was not registered
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
     * Reloads every registered raw and typed config from disk.
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
    }
}
