package me.dzusill.core.example.listener;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.event.AutoRegister;
import me.dzusill.core.event.CoreListener;
import me.dzusill.core.menu.MenuManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Example listener showing the {@link CoreListener} base and the {@link AutoRegister} marker.
 * Releases a player's menu context on quit so the manager does not retain stale references.
 */
@AutoRegister
public final class PlayerSessionListener extends CoreListener {

    private final MenuManager menus;

    public PlayerSessionListener(CorePlugin plugin, MenuManager menus) {
        super(plugin);
        this.menus = menus;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.forget(event.getPlayer());
    }
}
