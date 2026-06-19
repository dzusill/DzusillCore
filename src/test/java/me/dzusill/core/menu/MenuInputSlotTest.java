package me.dzusill.core.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;

class MenuInputSlotTest {

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

    private InputMenu openMenu() {
        PlayerMock player = server.addPlayer();
        MenuManager menus = plugin.services().get(MenuManager.class);
        InputMenu menu = new InputMenu(plugin, menus.context(player));
        menu.open();
        return menu;
    }

    @Test
    void declaresInputSlotAfterOpen() {
        InputMenu menu = openMenu();
        assertTrue(menu.isInputSlot(20));
        assertFalse(menu.isInputSlot(0));
    }

    @Test
    void clickOnNormalSlotIsCancelled() {
        InputMenu menu = openMenu();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getRawSlot()).thenReturn(0);

        menu.handleClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void clickOnInputSlotIsNotCancelled() {
        InputMenu menu = openMenu();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getRawSlot()).thenReturn(20);

        menu.handleClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void clickInPlayerInventoryIsNeverCancelled() {
        InputMenu menu = openMenu();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getRawSlot()).thenReturn(27); // first slot of the player's own (bottom) inventory

        menu.handleClick(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void listenerRoutesCloseToMenu() {
        InputMenu menu = openMenu();
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(menu.getInventory());

        new MenuListener(plugin).onClose(event);

        assertTrue(menu.closed);
    }

    /** Minimal menu with one display item and one input slot, recording close invocations. */
    private static final class InputMenu extends Menu {

        boolean closed = false;

        InputMenu(CorePlugin plugin, PlayerMenuContext context) {
            super(plugin, context);
        }

        @Override
        public Component title() {
            return Component.text("Input");
        }

        @Override
        public int size() {
            return 27;
        }

        @Override
        protected void decorate() {
            setItem(0, new ItemStack(Material.STONE));
            inputSlot(20);
        }

        @Override
        protected void onClose(InventoryCloseEvent event) {
            this.closed = true;
        }
    }
}
