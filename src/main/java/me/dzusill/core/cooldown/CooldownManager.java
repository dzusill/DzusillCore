package me.dzusill.core.cooldown;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A generic, type-safe cooldown tracker keyed by any identifier (commonly a player {@code UUID}). Each feature owns its
 * own instance with a fixed duration, replacing scattered timestamp maps.
 *
 * <pre>{@code
 * CooldownManager<UUID> teleport = new CooldownManager<>(3, TimeUnit.SECONDS);
 * if (teleport.isActive(player.getUniqueId())) { ... }
 * teleport.start(player.getUniqueId());
 * }</pre>
 *
 * @param <K>
 *            the cooldown key type
 */
public final class CooldownManager<K> {

    private final ConcurrentHashMap<K, Long> expiries = new ConcurrentHashMap<>();
    private final long durationMillis;

    public CooldownManager(long duration, TimeUnit unit) {
        this.durationMillis = unit.toMillis(duration);
    }

    /**
     * Starts (or restarts) the cooldown for the given key.
     */
    public void start(K key) {
        expiries.put(key, System.currentTimeMillis() + durationMillis);
    }

    /**
     * @return {@code true} if the key is still cooling down
     */
    public boolean isActive(K key) {
        Long expiry = expiries.get(key);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            expiries.remove(key);
            return false;
        }
        return true;
    }

    /**
     * @return milliseconds remaining on the cooldown, or {@code 0} if it is not active
     */
    public long remaining(K key) {
        Long expiry = expiries.get(key);
        if (expiry == null) {
            return 0L;
        }
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    /**
     * Clears the cooldown for a single key.
     */
    public void reset(K key) {
        expiries.remove(key);
    }

    /**
     * Clears every tracked cooldown.
     */
    public void clear() {
        expiries.clear();
    }
}
