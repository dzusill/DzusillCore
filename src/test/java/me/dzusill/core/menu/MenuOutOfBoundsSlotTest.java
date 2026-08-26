package me.dzusill.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;

/**
 * A {@code Slot:} in {@code menus.yml} copied from a bigger layout (or just mistyped) used to reach Bukkit's raw
 * inventory array and throw {@link ArrayIndexOutOfBoundsException} straight out of {@code decorate()}. {@code
 * openInternal} catches it and logs SEVERE, so the server survives - but only after two things already happened: the
 * rest of {@code decorate()} (everything after the bad slot) never ran, and {@code player.openInventory(...)} is never
 * reached, so the menu simply never opens. From the player's side that is indistinguishable from the command doing
 * nothing.
 *
 * <p>
 * Reproduces the client's report: {@code /ticket} on a 5-row hub whose filter buttons still carried the 6-row queue's
 * slots (47-51) threw {@code Index 48 out of bounds for length 45} and the menu never opened.
 * </p>
 *
 * <p>
 * Both bad slots are declared <em>before</em> the good one on purpose: catching the exception only where it happens to
 * land at the end of {@code decorate()} would still let an earlier bad slot blank everything after it, which is exactly
 * how the client's queue lost its ticket rows.
 * </p>
 */
class MenuOutOfBoundsSlotTest {

    private ServerMock server;
    private CorePlugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SmallMenu openMenu() {
        MenuManager menus = plugin.services().get(MenuManager.class);
        SmallMenu menu = new SmallMenu(plugin, menus.context(player));
        menu.open();
        return menu;
    }

    @Test
    void aBadSlotDoesNotStopTheMenuFromOpening() {
        SmallMenu menu = openMenu();

        assertSame(menu.getInventory(), player.getOpenInventory().getTopInventory(),
                "a bad Slot: value must not leave the open() call abandoned before openInventory() runs");
    }

    @Test
    void aGoodSlotDeclaredAfterABadOneStillRenders() {
        SmallMenu menu = openMenu();

        assertEquals(Material.STONE, menu.getInventory().getItem(3).getType(),
                "content after the bad slot in decorate() must not be skipped along with it");
    }

    /** {@code set()} at slot 48 and {@code button(51)} both land outside this 9-slot (1-row) inventory. */
    private static final class SmallMenu extends Menu {

        SmallMenu(CorePlugin plugin, PlayerMenuContext context) {
            super(plugin, context);
        }

        @Override
        public Component title() {
            return Component.text("Small");
        }

        @Override
        public int size() {
            return 9;
        }

        @Override
        protected void decorate() {
            setItem(48, new ItemStack(Material.DIAMOND));
            button(51).icon(new ItemStack(Material.GOLD_INGOT)).onClick(event -> {
            }).add();
            // Declared last: if a bad slot above aborts decorate() instead of being skipped, this never runs.
            setItem(3, new ItemStack(Material.STONE));
        }
    }
}
