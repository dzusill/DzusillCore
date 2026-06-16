package me.dzusill.core.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A self-contained, permission-aware menu node: an icon, an optional click handler, an optional
 * permission node and an optional visibility predicate. It is the per-slot analogue of a command's
 * {@code SubCommand} — just as the command tree hides children a sender cannot use and re-checks
 * permission before running, a button is hidden during render when the viewer fails its rule and is
 * re-checked before its handler fires.
 *
 * <p>Build one fluently from inside {@link Menu#decorate()}:</p>
 * <pre>{@code
 * button(13)
 *     .icon(new ItemBuilder(Material.DIAMOND).name("<aqua>Buy").build())
 *     .permission("core.shop.buy")
 *     .onClick(event -> ...)
 *     .add();
 * }</pre>
 */
public final class MenuButton {

    private final ItemStack icon;
    private final Consumer<InventoryClickEvent> onClick;
    private final String permission;
    private final Predicate<Player> visibleIf;

    private MenuButton(Builder builder) {
        this.icon = builder.icon;
        this.onClick = builder.onClick;
        this.permission = builder.permission;
        this.visibleIf = builder.visibleIf;
    }

    /** @return a standalone builder (use {@code build()}); prefer {@link Menu#button(int)} in a menu */
    public static Builder builder() {
        return new Builder(null, -1);
    }

    public ItemStack icon() {
        return icon;
    }

    public boolean hasHandler() {
        return onClick != null;
    }

    /**
     * @return whether this button should render for {@code player}: it passes when the permission is
     *         empty or held, and the visibility predicate (if any) accepts the player
     */
    public boolean visibleTo(Player player) {
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            return false;
        }
        return visibleIf == null || visibleIf.test(player);
    }

    /** @return whether {@code player} may trigger the click; same rule as {@link #visibleTo(Player)} */
    public boolean canClick(Player player) {
        return visibleTo(player);
    }

    void click(InventoryClickEvent event) {
        if (onClick != null) {
            onClick.accept(event);
        }
    }

    /**
     * Fluent builder for a {@link MenuButton}. When obtained from {@link Menu#button(int)} the
     * terminal {@link #add()} registers the button into that menu at the bound slot; the standalone
     * {@link MenuButton#builder()} form uses {@link #build()} instead (e.g. in tests).
     */
    public static final class Builder {

        private final Menu menu;
        private final int slot;
        private ItemStack icon;
        private Consumer<InventoryClickEvent> onClick;
        private String permission = "";
        private Predicate<Player> visibleIf;

        Builder(Menu menu, int slot) {
            this.menu = menu;
            this.slot = slot;
        }

        public Builder icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        public Builder onClick(Consumer<InventoryClickEvent> onClick) {
            this.onClick = onClick;
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder visibleIf(Predicate<Player> visibleIf) {
            this.visibleIf = visibleIf;
            return this;
        }

        /** @return the built button without registering it anywhere */
        public MenuButton build() {
            if (icon == null) {
                throw new IllegalStateException("MenuButton requires an icon()");
            }
            return new MenuButton(this);
        }

        /** Builds the button and registers it in the owning menu at the bound slot. */
        public void add() {
            if (menu == null) {
                throw new IllegalStateException("add() requires button(slot) from a Menu; use build() otherwise");
            }
            menu.placeButton(slot, build());
        }
    }
}
