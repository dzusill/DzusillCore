package me.dzusill.core.menu;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;
import me.dzusill.core.example.menu.ShopMenu;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MenuTemplateTest {

    private ServerMock server;
    private CorePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void borderedTemplateFillsEdgesAndContentFillsCenter() {
        PlayerMock player = server.addPlayer();
        MenuManager menus = plugin.services().get(MenuManager.class);

        new ShopMenu(plugin, menus.context(player)).open();
        Inventory inventory = player.getOpenInventory().getTopInventory();

        assertEquals(27, inventory.getSize());
        assertNotNull(inventory.getItem(0));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(0).getType());
        assertEquals(Material.DIAMOND, inventory.getItem(13).getType());
    }
}
