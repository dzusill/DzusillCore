package me.dzusill.core.menu;

import me.dzusill.core.service.Service;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the per-player {@link PlayerMenuContext} instances and provides menu lifecycle helpers.
 * Closes any framework menus that are still open during plugin shutdown so players are not left
 * staring at dead inventories.
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
     * Closes every open framework menu. Safe to call from {@code onDisable}.
     */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof Menu) {
                player.closeInventory();
            }
        }
        contexts.clear();
    }
}
