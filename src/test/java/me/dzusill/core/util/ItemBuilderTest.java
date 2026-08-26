package me.dzusill.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import be.seeseemelk.mockbukkit.MockBukkit;

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
        ItemStack item = new ItemBuilder(Material.DIAMOND, 3).name("<aqua>Shiny")
                .lore("<gray>Line one", "<gray>Line two").build();

        assertEquals(Material.DIAMOND, item.getType());
        assertEquals(3, item.getAmount());
        assertTrue(item.getItemMeta().hasDisplayName());
        assertEquals(2, item.getItemMeta().getLore().size());
    }

    @Test
    void preservesHexColoursInLegacyItemMetadata() {
        ItemStack item = new ItemBuilder(Material.DIAMOND).name("<#00F986>Shiny").lore("<#C21807>Line").build();

        assertTrue(item.getItemMeta().getDisplayName().startsWith("§x§0§0§f§9§8§6"));
        assertTrue(item.getItemMeta().getLore().get(0).startsWith("§x§c§2§1§8§0§7"));
    }

    @Test
    void glowAddsEnchantAndHidesIt() {
        ItemStack item = new ItemBuilder(Material.STICK).glow().build();

        assertTrue(item.getItemMeta().hasEnchants());
        assertTrue(item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    // No test for ItemBuilder.head()/skull(): SkullTextures reflects into the real CraftBukkit
    // SkullMeta's private GameProfile field, which MockBukkit's SkullMetaMock doesn't have (it
    // mocks Paper's PlayerProfile API instead). Texture rendering can only be verified manually
    // on a real server - see the Part A multi-version smoke-test matrix in the project plan.
}
