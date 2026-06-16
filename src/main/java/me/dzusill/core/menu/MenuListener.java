package me.dzusill.core.menu;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.event.CoreListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Single entry point for all GUI input. Recovers the {@link Menu} from the inventory holder and
 * routes clicks (and close events) to it; drags into a menu are cancelled unless every affected slot
 * is an input slot. This holder-based dispatch means individual menus never register their own
 * listeners.
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
        if (event.getInventory().getHolder() instanceof Menu menu) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < menu.size() && !menu.isInputSlot(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            menu.onClose(event);
        }
    }
}
