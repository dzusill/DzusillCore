package me.dzusill.core.menu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.menu.meta.MenuMeta;
import me.dzusill.core.menu.template.MenuTemplate;
import me.dzusill.core.util.ColorUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Base class for all GUIs. Implements {@link InventoryHolder} so the central {@link MenuListener} can recover the
 * owning {@code Menu} from any inventory and route clicks back to it, without a separate registry.
 *
 * <p>
 * Subclasses declare their title and size either by annotating the class with {@link MenuMeta} or by overriding
 * {@link #title()} / {@link #size()}, then place content in {@link #decorate()} using the fluent {@link #button(int)}
 * API (permission-aware) or the lower level {@link #set} / {@link #setItem}. A reusable {@link MenuTemplate} (returned
 * from {@link #template()}) is applied before {@code decorate()}, so common layouts (borders, fillers, close buttons)
 * are not repeated in every menu.
 * </p>
 */
public abstract class Menu implements InventoryHolder {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    protected final CorePlugin plugin;
    protected final PlayerMenuContext context;
    protected Inventory inventory;

    private final Map<Integer, MenuItem> items = new HashMap<>();
    private final Map<Integer, MenuButton> buttons = new HashMap<>();
    private final Set<Integer> inputSlots = new HashSet<>();

    private final String metaTitle;
    private final int metaSize;
    private final String permission;

    protected Menu(CorePlugin plugin, PlayerMenuContext context) {
        this.plugin = plugin;
        this.context = context;
        MenuMeta meta = getClass().getAnnotation(MenuMeta.class);
        if (meta != null) {
            this.metaTitle = meta.title();
            this.metaSize = meta.size();
            this.permission = meta.permission();
        } else {
            this.metaTitle = null;
            this.metaSize = -1;
            this.permission = "";
        }
    }

    /**
     * @return the inventory title, taken from {@link MenuMeta#title()} unless overridden
     * @throws IllegalStateException
     *             if the menu is neither annotated nor overrides this method
     */
    public Component title() {
        if (metaTitle == null) {
            throw new IllegalStateException(
                    getClass().getName() + " must be annotated with @MenuMeta or override title()");
        }
        return ColorUtils.parse(metaTitle);
    }

    /**
     * @return the inventory size in slots (a multiple of 9), from {@link MenuMeta#size()} unless overridden
     * @throws IllegalStateException
     *             if the menu is neither annotated nor overrides this method
     */
    public int size() {
        if (metaSize < 0) {
            throw new IllegalStateException(
                    getClass().getName() + " must be annotated with @MenuMeta or override size()");
        }
        return metaSize;
    }

    /** @return the permission required to open through the registry; empty means no check */
    public String permission() {
        return permission;
    }

    /** Places this menu's content. Called after the template is applied. */
    protected abstract void decorate();

    /** @return a reusable layout template applied before {@link #decorate()}, or {@code null} */
    protected MenuTemplate template() {
        return null;
    }

    /**
     * Builds the inventory, applies the template and content, and opens it for the player. Records the previously open
     * menu in the navigation history so {@link #back()} can return to it.
     */
    public void open() {
        openInternal(true);
    }

    private void openInternal(boolean recordHistory) {
        // Bukkit.createInventory(InventoryHolder, int, Component) is a Paper-only overload;
        // serialize to a legacy section-sign string so this works on plain Spigot/CraftBukkit too.
        this.inventory = Bukkit.createInventory(this, size(), LEGACY_SECTION.serialize(title()));
        this.items.clear();
        this.buttons.clear();
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
     * Begins declaring a permission-aware {@link MenuButton} at {@code slot}. Configure it fluently and finish with
     * {@code add()}:
     *
     * <pre>{@code
     * button(13).icon(icon).permission("core.shop.buy").onClick(event -> ...).add();
     * }</pre>
     *
     * A button whose permission/visibility rule the viewer fails is hidden during render and its click is ignored,
     * mirroring how the command tree hides and guards subcommands.
     */
    protected MenuButton.Builder button(int slot) {
        return new MenuButton.Builder(this, slot);
    }

    void placeButton(int slot, MenuButton button) {
        buttons.put(slot, button);
        if (inventory != null && button.visibleTo(context.player())) {
            inventory.setItem(slot, button.icon());
        }
    }

    /**
     * Marks a slot as a free-interaction "input" slot: clicks and drags into it are not cancelled, so the player can
     * place and take an item (e.g. an item to be edited). Call from {@link #decorate()}; declarations are cleared and
     * re-applied on every open/refresh.
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
        Player player = context.player();
        for (Map.Entry<Integer, MenuButton> entry : buttons.entrySet()) {
            MenuButton button = entry.getValue();
            if (button.visibleTo(player)) {
                inventory.setItem(entry.getKey(), button.icon());
            }
        }
    }

    /**
     * Dispatches a click within this menu to the clicked slot's handler. Cancels the event to prevent item theft,
     * except on declared {@link #inputSlot(int) input slots} where the player is allowed to place and take items.
     * Clicks in the player's own (bottom) inventory are never the menu's business and are always left alone &mdash;
     * otherwise picking an item up from your own inventory while a menu is open (the first half of dragging it into an
     * input slot) would itself get cancelled, since its raw slot can never be a declared (top-inventory) input slot.
     */
    void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= size() || inputSlots.contains(slot)) {
            return;
        }
        event.setCancelled(true);
        MenuButton button = buttons.get(slot);
        if (button != null) {
            if (event.getWhoClicked() instanceof Player player && button.canClick(player)) {
                button.click(event);
            }
            return;
        }
        MenuItem item = items.get(slot);
        if (item != null) {
            item.click(event);
        }
    }

    /**
     * Invoked when this menu's inventory is closed. Override to react (e.g. return an item left in an input slot to the
     * player). Default is a no-op.
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
