package me.dzusill.core.storage;

import java.util.function.Function;

import me.dzusill.core.config.Config;

/**
 * A YAML-backed {@link DataStore} keyed by string under a base path. Values are converted to and from the YAML
 * primitive representation by the supplied serializer/deserializer, so the same store works for any value type (e.g. a
 * serialized location string, a number, a model).
 *
 * @param <V>
 *            value type
 */
public final class YamlDataStore<V> extends AbstractDataStore<String, V> {

    private final Config config;
    private final String basePath;
    private final Function<V, Object> serializer;
    private final Function<Object, V> deserializer;

    /**
     * @param config
     *            backing config (already loaded)
     * @param basePath
     *            section under which entries are stored
     * @param serializer
     *            converts a value to a YAML-storable object
     * @param deserializer
     *            reconstructs a value from its stored object
     */
    public YamlDataStore(Config config, String basePath, Function<V, Object> serializer,
            Function<Object, V> deserializer) {
        this.config = config;
        this.basePath = basePath;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    private String pathOf(String key) {
        return basePath + "." + key;
    }

    @Override
    public void load() {
        cache.clear();
        if (!config.isConfigurationSection(basePath)) {
            return;
        }
        for (String key : config.getConfigurationSection(basePath).getKeys(false)) {
            Object stored = config.get(pathOf(key));
            if (stored != null) {
                cache.put(key, deserializer.apply(stored));
            }
        }
    }

    @Override
    public void save() {
        config.set(basePath, null);
        cache.forEach((key, value) -> config.set(pathOf(key), serializer.apply(value)));
        config.save();
    }
}
