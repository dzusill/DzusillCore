package me.dzusill.core.menu;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

/**
 * Per-player companion object that carries state across the menu system: arbitrary transfer data (e.g. the item being
 * edited), the currently open menu, and a navigation history used to power a generic "back" button. Each online player
 * has exactly one context, owned by the {@link MenuManager}.
 */
public final class PlayerMenuContext {

    private final Player owner;
    private final Map<String, Object> data = new HashMap<>();
    private final Deque<Menu> history = new ArrayDeque<>();
    private Menu current;

    public PlayerMenuContext(Player owner) {
        this.owner = owner;
    }

    public Player player() {
        return owner;
    }

    /**
     * Stores an arbitrary value to transfer between menus.
     */
    public PlayerMenuContext set(String key, Object value) {
        data.put(key, value);
        return this;
    }

    /**
     * @return the stored value cast to the requested type, or {@code null} if absent
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    Menu current() {
        return current;
    }

    void setCurrent(Menu menu) {
        this.current = menu;
    }

    void pushHistory(Menu menu) {
        history.push(menu);
    }

    Menu popHistory() {
        return history.poll();
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    /**
     * Clears navigation history; typically called when a menu session ends.
     */
    public void clearHistory() {
        history.clear();
        current = null;
    }
}
