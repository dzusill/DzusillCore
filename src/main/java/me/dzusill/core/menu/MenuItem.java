package me.dzusill.core.menu;

import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Pairs the visual {@link ItemStack} shown in a slot with the behaviour triggered when it is clicked. Click handling is
 * per-item (a {@link Consumer}) rather than a central switch, so menus stay declarative and slots are self-contained.
 */
public final class MenuItem {

    private final ItemStack itemStack;
    private final Consumer<InventoryClickEvent> clickHandler;

    private MenuItem(ItemStack itemStack, Consumer<InventoryClickEvent> clickHandler) {
        this.itemStack = itemStack;
        this.clickHandler = clickHandler;
    }

    /**
     * A purely decorative item with no click behaviour.
     */
    public static MenuItem display(ItemStack itemStack) {
        return new MenuItem(itemStack, null);
    }

    /**
     * A clickable item.
     */
    public static MenuItem of(ItemStack itemStack, Consumer<InventoryClickEvent> clickHandler) {
        return new MenuItem(itemStack, clickHandler);
    }

    public ItemStack itemStack() {
        return itemStack;
    }

    public boolean hasHandler() {
        return clickHandler != null;
    }

    void click(InventoryClickEvent event) {
        if (clickHandler != null) {
            clickHandler.accept(event);
        }
    }
}
