package me.dzusill.core.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import be.seeseemelk.mockbukkit.MockBukkit;

/**
 * Pure visibility/click-guard logic, the menu analogue of a command's per-node permission gating. Exercised without
 * opening an inventory (mocked {@link Player}, MockBukkit only for {@code
 * ItemStack} construction).
 */
class MenuButtonTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack icon() {
        return new ItemStack(Material.DIAMOND);
    }

    @Test
    void noPermissionIsAlwaysVisible() {
        MenuButton button = MenuButton.builder().icon(icon()).build();
        Player player = mock(Player.class);

        assertTrue(button.visibleTo(player));
        assertTrue(button.canClick(player));
    }

    @Test
    void permissionGatesVisibilityAndClick() {
        MenuButton button = MenuButton.builder().icon(icon()).permission("core.x").build();

        Player allowed = mock(Player.class);
        when(allowed.hasPermission("core.x")).thenReturn(true);
        Player denied = mock(Player.class);
        when(denied.hasPermission("core.x")).thenReturn(false);

        assertTrue(button.visibleTo(allowed));
        assertTrue(button.canClick(allowed));
        assertFalse(button.visibleTo(denied));
        assertFalse(button.canClick(denied));
    }

    @Test
    void visibilityPredicateApplies() {
        MenuButton button = MenuButton.builder().icon(icon()).visibleIf(Player::isOp).build();

        Player op = mock(Player.class);
        when(op.isOp()).thenReturn(true);
        Player notOp = mock(Player.class);
        when(notOp.isOp()).thenReturn(false);

        assertTrue(button.visibleTo(op));
        assertFalse(button.visibleTo(notOp));
    }

    @Test
    void buildRequiresIcon() {
        assertThrows(IllegalStateException.class, () -> MenuButton.builder().build());
    }
}
