package me.dzusill.core.menu.template;

import org.bukkit.inventory.ItemStack;

import me.dzusill.core.menu.Menu;

/**
 * Base class providing the layout primitives shared by concrete templates: filling the perimeter (border) or every
 * empty slot. Subclasses compose these in {@link #apply(Menu)}.
 */
public abstract class AbstractMenuTemplate implements MenuTemplate {

    private static final int ROW_WIDTH = 9;

    /**
     * Fills the outer ring of the menu (top/bottom rows and left/right columns) with {@code filler}.
     */
    protected void applyBorder(Menu menu, ItemStack filler) {
        int size = menu.size();
        int rows = size / ROW_WIDTH;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / ROW_WIDTH;
            int column = slot % ROW_WIDTH;
            boolean edge = row == 0 || row == rows - 1 || column == 0 || column == ROW_WIDTH - 1;
            if (edge) {
                menu.setItem(slot, filler);
            }
        }
    }

    /**
     * Fills every currently-empty slot with {@code filler}. Applied before the menu's own content, so
     * {@code decorate()} overwrites the slots it uses.
     */
    protected void applyFill(Menu menu, ItemStack filler) {
        menu.fillEmpty(filler);
    }
}
