package me.dzusill.core.storage;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base {@link DataStore} that keeps an in-memory cache and delegates only the persistence concern to subclasses via
 * {@link #load()} and {@link #save()}. All read/write operations run against the cache, giving consistent semantics
 * regardless of the backing medium.
 *
 * @param <K>
 *            key type
 * @param <V>
 *            value type
 */
public abstract class AbstractDataStore<K, V> implements DataStore<K, V> {

    protected final Map<K, V> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<V> get(K key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public boolean contains(K key) {
        return cache.containsKey(key);
    }

    @Override
    public Set<K> keys() {
        return Set.copyOf(cache.keySet());
    }
}
