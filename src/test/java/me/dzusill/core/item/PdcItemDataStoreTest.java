package me.dzusill.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;

class PdcItemDataStoreTest {

    private CorePlugin plugin;
    private ItemDataStore store;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
        store = new PdcItemDataStore(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void missingKeyReturnsDefaults() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        assertEquals(0, store.getInt(item, "kills"));
        assertFalse(store.hasKey(item, "kills"));
        assertTrue(store.keys(item).isEmpty());
    }

    @Test
    void setAndGetIntRoundTrips() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        item = store.setInt(item, "kills", 42);

        assertTrue(store.hasKey(item, "kills"));
        assertEquals(42, store.getInt(item, "kills"));
        assertTrue(store.keys(item).contains("kills"));
    }

    @Test
    void setAndGetStringRoundTrips() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        item = store.setString(item, "tag", "block");

        assertTrue(store.hasKey(item, "tag"));
        assertEquals("block", store.getString(item, "tag"));
    }

    @Test
    void removeKeyClearsValue() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        item = store.setInt(item, "kills", 7);
        item = store.removeKey(item, "kills");

        assertFalse(store.hasKey(item, "kills"));
        assertEquals(0, store.getInt(item, "kills"));
    }

    @Test
    void keysAreCaseInsensitiveAndNamespaced() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        item = store.setInt(item, "Kills", 1);

        // stored lower-cased, so lookups by any case resolve to the same entry
        assertTrue(store.hasKey(item, "kills"));
        assertEquals(1, store.getInt(item, "KILLS"));
    }
}
