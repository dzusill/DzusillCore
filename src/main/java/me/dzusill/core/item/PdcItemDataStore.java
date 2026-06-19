package me.dzusill.core.item;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Native {@link ItemDataStore} backed by the Bukkit {@link PersistentDataContainer}. The default, dependency-free
 * implementation: keys are namespaced to the owning plugin, so they never collide with vanilla NBT or other plugins.
 *
 * <p>
 * Keys are normalised to lower case to satisfy {@link NamespacedKey}'s charset; choose {@link NbtApiItemDataStore}
 * instead when an existing item format relies on raw, case-sensitive NBT root tags.
 * </p>
 */
public final class PdcItemDataStore implements ItemDataStore {

    private final Plugin plugin;

    public PdcItemDataStore(Plugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(String key) {
        return new NamespacedKey(plugin, key.toLowerCase(Locale.ROOT));
    }

    @Override
    public int getInt(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        return meta.getPersistentDataContainer().getOrDefault(key(key), PersistentDataType.INTEGER, 0);
    }

    @Override
    public ItemStack setInt(ItemStack item, String key, int value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key(key), PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public String getString(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key(key), PersistentDataType.STRING);
    }

    @Override
    public ItemStack setString(ItemStack item, String key, String value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean hasKey(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(key(key), PersistentDataType.INTEGER)
                || container.has(key(key), PersistentDataType.STRING);
    }

    @Override
    public ItemStack removeKey(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().remove(key(key));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Set<String> keys(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (NamespacedKey namespacedKey : meta.getPersistentDataContainer().getKeys()) {
            if (namespacedKey.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) {
                result.add(namespacedKey.getKey());
            }
        }
        return result;
    }
}
