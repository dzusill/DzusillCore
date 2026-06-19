package me.dzusill.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;
import me.dzusill.core.example.menu.ShopMenu;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

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

        ShopMenu menu = new ShopMenu(plugin, menus.context(player));
        menu.open();
        // Read the inventory straight off the Menu (InventoryHolder) rather than through
        // Player#getOpenInventory(): InventoryView is a class on older Bukkit and an interface
        // on newer Bukkit, so touching it directly is a cross-version landmine in tests that mix
        // a MockBukkit line with a different-shaped Bukkit API on the classpath.
        Inventory inventory = menu.getInventory();

        assertEquals(27, inventory.getSize());
        assertNotNull(inventory.getItem(0));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItem(0).getType());
        assertEquals(Material.DIAMOND, inventory.getItem(13).getType());
    }
}
