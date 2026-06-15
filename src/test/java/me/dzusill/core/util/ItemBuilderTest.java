package me.dzusill.core.util;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemBuilderTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void buildsItemWithAmountNameAndLore() {
        ItemStack item = new ItemBuilder(Material.DIAMOND, 3)
                .name("<aqua>Shiny")
                .lore("<gray>Line one", "<gray>Line two")
                .build();

        assertEquals(Material.DIAMOND, item.getType());
        assertEquals(3, item.getAmount());
        assertTrue(item.getItemMeta().hasDisplayName());
        assertEquals(2, item.getItemMeta().lore().size());
    }

    @Test
    void glowAddsEnchantAndHidesIt() {
        ItemStack item = new ItemBuilder(Material.STICK).glow().build();

        assertTrue(item.getItemMeta().hasEnchants());
        assertTrue(item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }
}
