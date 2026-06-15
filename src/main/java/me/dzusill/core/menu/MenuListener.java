package me.dzusill.core.menu;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.event.CoreListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Single entry point for all GUI input. Recovers the {@link Menu} from the inventory holder and
 * routes clicks to it; drags into a menu are always cancelled. This holder-based dispatch means
 * individual menus never register their own listeners.
 */
public final class MenuListener extends CoreListener {

    public MenuListener(CorePlugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            menu.handleClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
