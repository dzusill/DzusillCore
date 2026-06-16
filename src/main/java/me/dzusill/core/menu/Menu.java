package me.dzusill.core.menu;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.menu.template.MenuTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all GUIs. Implements {@link InventoryHolder} so the central {@link MenuListener}
 * can recover the owning {@code Menu} from any inventory and route clicks back to it, without a
 * separate registry.
 *
 * <p>Subclasses declare {@link #title()} and {@link #size()} and place their content in
 * {@link #decorate()} using {@link #set} / {@link #setItem}. A reusable {@link MenuTemplate}
 * (returned from {@link #template()}) is applied before {@code decorate()}, so common layouts
 * (borders, fillers, close buttons) are not repeated in every menu.</p>
 */
public abstract class Menu implements InventoryHolder {

    protected final CorePlugin plugin;
    protected final PlayerMenuContext context;
    protected Inventory inventory;

    private final Map<Integer, MenuItem> items = new HashMap<>();
    private final Set<Integer> inputSlots = new HashSet<>();

    protected Menu(CorePlugin plugin, PlayerMenuContext context) {
        this.plugin = plugin;
        this.context = context;
    }

    /** @return the inventory title */
    public abstract Component title();

    /** @return the inventory size in slots (a multiple of 9) */
    public abstract int size();

    /** Places this menu's content. Called after the template is applied. */
    protected abstract void decorate();

    /** @return a reusable layout template applied before {@link #decorate()}, or {@code null} */
    protected MenuTemplate template() {
        return null;
    }

    /**
     * Builds the inventory, applies the template and content, and opens it for the player. Records
     * the previously open menu in the navigation history so {@link #back()} can return to it.
     */
    public void open() {
        openInternal(true);
    }

    private void openInternal(boolean recordHistory) {
        this.inventory = Bukkit.createInventory(this, size(), title());
        this.items.clear();
        this.inputSlots.clear();

        MenuTemplate template = template();
        if (template != null) {
            template.apply(this);
        }
        decorate();
        render();

        if (recordHistory && context.current() != null && context.current() != this) {
            context.pushHistory(context.current());
        }
        context.setCurrent(this);
        context.player().openInventory(inventory);
    }

    /**
     * Rebuilds and reopens the menu in place (e.g. after a state change or page switch).
     */
    public void refresh() {
        openInternal(false);
    }

    /**
     * Reopens the previous menu in the navigation history, or closes if there is none.
     */
    public void back() {
        Menu previous = context.popHistory();
        if (previous != null) {
            previous.openInternal(false);
        } else {
            close();
        }
    }

    /**
     * Closes this menu for the player.
     */
    public void close() {
        context.player().closeInventory();
    }

    // --- content placement (also used by templates) -------------------------

    public void set(int slot, MenuItem item) {
        items.put(slot, item);
        if (inventory != null) {
            inventory.setItem(slot, item.itemStack());
        }
    }

    public void setItem(int slot, ItemStack itemStack) {
        set(slot, MenuItem.display(itemStack));
    }

    /**
     * Marks a slot as a free-interaction "input" slot: clicks and drags into it are not cancelled,
     * so the player can place and take an item (e.g. an item to be edited). Call from
     * {@link #decorate()}; declarations are cleared and re-applied on every open/refresh.
     */
    protected void inputSlot(int slot) {
        inputSlots.add(slot);
    }

    /**
     * @return whether {@code slot} (a raw, top-inventory slot index) is an input slot
     */
    boolean isInputSlot(int slot) {
        return inputSlots.contains(slot);
    }

    /**
     * Fills every currently-empty slot with a non-clickable filler item.
     */
    public void fillEmpty(ItemStack filler) {
        for (int slot = 0; slot < size(); slot++) {
            if (!items.containsKey(slot)) {
                set(slot, MenuItem.display(filler));
            }
        }
    }

    private void render() {
        for (Map.Entry<Integer, MenuItem> entry : items.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue().itemStack());
        }
    }

    /**
     * Dispatches a click within this menu to the clicked slot's handler. Cancels the event to
     * prevent item theft, except on declared {@link #inputSlot(int) input slots} where the player is
     * allowed to place and take items.
     */
    void handleClick(InventoryClickEvent event) {
        if (inputSlots.contains(event.getRawSlot())) {
            return;
        }
        event.setCancelled(true);
        MenuItem item = items.get(event.getRawSlot());
        if (item != null) {
            item.click(event);
        }
    }

    /**
     * Invoked when this menu's inventory is closed. Override to react (e.g. return an item left in an
     * input slot to the player). Default is a no-op.
     */
    protected void onClose(InventoryCloseEvent event) {
    }

    public PlayerMenuContext context() {
        return context;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
