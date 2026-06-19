package me.dzusill.core.storage;

import java.util.Optional;
import java.util.Set;

/**
 * Abstraction over a keyed collection of values with explicit persistence. Decoupling call sites from the backing
 * medium (YAML now, SQL later) behind this interface means storage can be swapped without touching business logic.
 *
 * @param <K>
 *            key type
 * @param <V>
 *            value type
 */
public interface DataStore<K, V> {

    Optional<V> get(K key);

    void put(K key, V value);

    void remove(K key);

    boolean contains(K key);

    Set<K> keys();

    /**
     * Loads all entries from the backing medium into memory.
     */
    void load();

    /**
     * Persists all in-memory entries to the backing medium.
     */
    void save();
}
