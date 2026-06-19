package me.dzusill.core.menu;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.util.ItemBuilder;

/**
 * A {@link Menu} that lays a variable-length list of {@link MenuItem}s across multiple pages and wires up
 * previous/next/close navigation automatically. Subclasses only supply the full content via {@link #content()}; paging,
 * slot placement and the navigation bar are handled here.
 *
 * <p>
 * By default the bottom row is reserved for navigation and all rows above it hold content.
 * </p>
 */
public abstract class PaginatedMenu extends Menu {

    private static final int ROW_WIDTH = 9;

    protected int page = 0;

    protected PaginatedMenu(CorePlugin plugin, PlayerMenuContext context) {
        super(plugin, context);
    }

    /**
     * @return the full, unpaged list of content items
     */
    protected abstract List<MenuItem> content();

    /**
     * Hook for subclasses to add fixed decoration after content and navigation are placed.
     */
    protected void decoratePage() {
    }

    /**
     * @return the slots used for content; defaults to every slot above the bottom navigation row
     */
    protected int[] contentSlots() {
        int count = size() - ROW_WIDTH;
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    protected final void decorate() {
        List<MenuItem> all = content();
        int[] slots = contentSlots();
        int perPage = slots.length;
        int maxPage = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        page = Math.max(0, Math.min(page, maxPage - 1));

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < all.size(); i++) {
            set(slots[i], all.get(start + i));
        }

        placeNavigation(maxPage);
        decoratePage();
    }

    private void placeNavigation(int maxPage) {
        int navRow = size() - ROW_WIDTH;

        if (page > 0) {
            set(navRow, MenuItem.of(previousButton(), event -> {
                page--;
                refresh();
            }));
        }
        if (page < maxPage - 1) {
            set(navRow + 8, MenuItem.of(nextButton(), event -> {
                page++;
                refresh();
            }));
        }
        set(navRow + 4, MenuItem.of(closeButton(), event -> close()));
    }

    protected ItemStack previousButton() {
        return new ItemBuilder(Material.ARROW).name("<yellow>Previous page").build();
    }

    protected ItemStack nextButton() {
        return new ItemBuilder(Material.ARROW).name("<yellow>Next page").build();
    }

    protected ItemStack closeButton() {
        return new ItemBuilder(Material.BARRIER).name("<red>Close").build();
    }
}
