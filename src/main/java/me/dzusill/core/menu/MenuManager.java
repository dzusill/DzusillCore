package me.dzusill.core.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.service.Service;

/**
 * Owns the per-player {@link PlayerMenuContext} instances and provides menu lifecycle helpers. Closes any framework
 * menus that are still open during plugin shutdown so players are not left staring at dead inventories.
 *
 * <p>
 * Each context captures the live {@link Player} object, so a player's context <em>must</em> be released when they
 * disconnect — otherwise a reconnect (Bukkit hands out a fresh {@code Player} for the same UUID) would keep resolving
 * the stale, now-offline handle, and every {@code menu.open()} would call {@code openInventory} on it: a silent no-op
 * with nothing logged ("the GUI just doesn't open"). That cleanup used to be the caller's job and was universally
 * forgotten, so the manager now owns it: the constructor takes the {@link CorePlugin} and self-registers a
 * {@link PlayerQuitEvent} listener that calls {@link #forget(Player)}. Requiring the plugin makes the cleanup
 * impossible to skip — there is no longer a no-arg constructor that quietly leaks.
 * </p>
 */
public final class MenuManager implements Service {

    private final Map<UUID, PlayerMenuContext> contexts = new ConcurrentHashMap<>();

    /**
     * @param plugin
     *            the owning plugin, used to self-register the quit-cleanup listener. Mandatory: this is what guarantees
     *            a player's menu context is released on disconnect so menus never open against a stale Player handle.
     */
    public MenuManager(CorePlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new QuitCleanupListener(), plugin);
    }

    /**
     * @return the player's context, creating it on first access. If a context already exists for this UUID it is
     *         rebound to the given (live) {@link Player} object — a defensive backstop in case the quit cleanup was
     *         missed (e.g. a hard crash or forced reload), so a menu can never be opened against a disconnected handle.
     */
    public PlayerMenuContext context(Player player) {
        PlayerMenuContext context = contexts.computeIfAbsent(player.getUniqueId(), id -> new PlayerMenuContext(player));
        context.bind(player);
        return context;
    }

    /**
     * Convenience for {@code menu.open()}.
     */
    public void open(Menu menu) {
        menu.open();
    }

    /**
     * Discards a player's context (e.g. on quit) to avoid leaking references. Idempotent. Called automatically when the
     * player disconnects; also safe to call manually.
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

    /**
     * Releases a player's context the moment they disconnect. Registered by the constructor so consumers cannot forget
     * to wire it (the historical cause of stale-handle GUIs that silently refused to open after a relog).
     */
    private final class QuitCleanupListener implements Listener {

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            forget(event.getPlayer());
        }
    }
}
