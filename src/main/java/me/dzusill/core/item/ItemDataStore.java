package me.dzusill.core.item;

import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Reads and writes small, typed values directly on an {@link ItemStack} (the analogue of
 * {@code PersistentDataContainer} for items, exposed as a tiny key/value API). This is the missing
 * counterpart to the player-record {@code storage}/{@code database} layers: it covers plugins that
 * persist state <em>on the item itself</em> (durability counters, ownership tags, stat trackers).
 *
 * <p>Implementations are provider-agnostic. {@link PdcItemDataStore} uses the native Bukkit
 * persistent-data container (no external dependency, the default) while {@link NbtApiItemDataStore}
 * uses NBTAPI for plugins that need raw, un-namespaced NBT keys (e.g. to keep an existing item
 * format readable across versions).</p>
 *
 * <p>ItemStacks are value types: every mutating method returns the updated stack, which callers must
 * use in place of the original.</p>
 */
public interface ItemDataStore {

    /**
     * @return the integer stored under {@code key}, or {@code 0} if absent
     */
    int getInt(ItemStack item, String key);

    /**
     * Stores an integer under {@code key}.
     *
     * @return the updated item
     */
    ItemStack setInt(ItemStack item, String key, int value);

    /**
     * @return the string stored under {@code key}, or {@code null} if absent
     */
    String getString(ItemStack item, String key);

    /**
     * Stores a string under {@code key}.
     *
     * @return the updated item
     */
    ItemStack setString(ItemStack item, String key, String value);

    /**
     * @return whether the item carries a value under {@code key}
     */
    boolean hasKey(ItemStack item, String key);

    /**
     * Removes the value under {@code key} if present.
     *
     * @return the updated item
     */
    ItemStack removeKey(ItemStack item, String key);

    /**
     * @return the set of keys written by this store that are present on the item
     */
    Set<String> keys(ItemStack item);
}
