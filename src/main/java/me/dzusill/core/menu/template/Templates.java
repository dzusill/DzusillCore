package me.dzusill.core.menu.template;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.dzusill.core.menu.Menu;
import me.dzusill.core.util.ItemBuilder;

/**
 * Factory of ready-made {@link MenuTemplate}s. These are size-agnostic, so the same template works for any inventory
 * size, letting menus opt into a standard look with a single line.
 */
public final class Templates {

    private Templates() {
    }

    /**
     * Border of {@code filler} around the menu edge; the interior is left for the menu's content.
     */
    public static MenuTemplate bordered(ItemStack filler) {
        return new AbstractMenuTemplate() {
            @Override
            public void apply(Menu menu) {
                applyBorder(menu, filler);
            }
        };
    }

    /**
     * Border using the default gray-glass filler.
     */
    public static MenuTemplate bordered() {
        return bordered(defaultFiller());
    }

    /**
     * Fills the whole background with {@code filler}; the menu's content overwrites the slots it uses.
     */
    public static MenuTemplate filled(ItemStack filler) {
        return new AbstractMenuTemplate() {
            @Override
            public void apply(Menu menu) {
                applyFill(menu, filler);
            }
        };
    }

    public static MenuTemplate filled() {
        return filled(defaultFiller());
    }

    private static ItemStack defaultFiller() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    }
}
