package me.dzusill.core.item;

import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * {@link ItemDataStore} backed by NBTAPI, writing raw, un-namespaced NBT root tags. Use this (over
 * {@link PdcItemDataStore}) when a plugin must read or preserve an existing case-sensitive item
 * format — e.g. legacy items tagged {@code Playerkills}/{@code StatTag} that predate the persistent
 * data container.
 *
 * <p>Requires the NBTAPI plugin on the server ({@code depend: [NBTAPI]}). Because NBTAPI resolves
 * NMS at runtime it cannot run under MockBukkit; cover the calling logic with unit tests against
 * {@link PdcItemDataStore} and exercise this implementation on a live server.</p>
 */
public final class NbtApiItemDataStore implements ItemDataStore {

    @Override
    public int getInt(ItemStack item, String key) {
        return new NBTItem(item).getInteger(key);
    }

    @Override
    public ItemStack setInt(ItemStack item, String key, int value) {
        NBTItem nbti = new NBTItem(item);
        nbti.setInteger(key, value);
        return nbti.getItem();
    }

    @Override
    public String getString(ItemStack item, String key) {
        return new NBTItem(item).getString(key);
    }

    @Override
    public ItemStack setString(ItemStack item, String key, String value) {
        NBTItem nbti = new NBTItem(item);
        nbti.setString(key, value);
        return nbti.getItem();
    }

    @Override
    public boolean hasKey(ItemStack item, String key) {
        return new NBTItem(item).hasTag(key);
    }

    @Override
    public ItemStack removeKey(ItemStack item, String key) {
        NBTItem nbti = new NBTItem(item);
        nbti.removeKey(key);
        return nbti.getItem();
    }

    @Override
    public Set<String> keys(ItemStack item) {
        return new NBTItem(item).getKeys();
    }
}
