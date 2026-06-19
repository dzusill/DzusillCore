package me.dzusill.core.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import me.dzusill.core.service.Service;

/**
 * Owns the per-player {@link PlayerMenuContext} instances and provides menu lifecycle helpers. Closes any framework
 * menus that are still open during plugin shutdown so players are not left staring at dead inventories.
 */
public final class MenuManager implements Service {

    private final Map<UUID, PlayerMenuContext> contexts = new ConcurrentHashMap<>();

    /**
     * @return the player's context, creating it on first access
     */
    public PlayerMenuContext context(Player player) {
        return contexts.computeIfAbsent(player.getUniqueId(), id -> new PlayerMenuContext(player));
    }

    /**
     * Convenience for {@code menu.open()}.
     */
    public void open(Menu menu) {
        menu.open();
    }

    /**
     * Discards a player's context (e.g. on quit) to avoid leaking references.
     */
    public void forget(Player player) {
        contexts.remove(player.getUniqueId());
    }

    /**
     * Refreshes every online player's currently open menu if it's an instance of {@code menuType}. For broadcasting a
     * backend state change (e.g. a lottery draw completing) to everyone currently looking at a menu backed by that
     * state, without each menu having to poll for changes itself.
     */
    public void refreshAll(Class<? extends Menu> menuType) {
        for (PlayerMenuContext context : contexts.values()) {
            Menu current = context.current();
            if (menuType.isInstance(current)) {
                current.refresh();
            }
        }
    }

    /**
     * Closes every player's currently open menu if it's an instance of {@code menuType}. Use at state-transition points
     * where showing stale UI would confuse the player.
     */
    public void closeAll(Class<? extends Menu> menuType) {
        for (PlayerMenuContext context : contexts.values()) {
            Menu current = context.current();
            if (menuType.isInstance(current)) {
                context.player().closeInventory();
            }
        }
    }

    /**
     * Closes every open framework menu. Safe to call from {@code onDisable}.
     *
     * <p>
     * Deliberately goes through our own tracked {@link PlayerMenuContext#current()} rather than
     * {@code Player#getOpenInventory()}: {@code InventoryView} is a class on older Bukkit and an interface on newer
     * Bukkit, so code compiled against one shape can throw {@code IncompatibleClassChangeError} against a server
     * running the other.
     * </p>
     */
    public void closeAll() {
        for (PlayerMenuContext context : contexts.values()) {
            if (context.current() != null) {
                context.player().closeInventory();
            }
        }
        contexts.clear();
    }
}
