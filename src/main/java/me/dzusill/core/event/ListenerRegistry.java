package me.dzusill.core.event;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.service.Service;

/**
 * Central registration point for event listeners. Registering through one service keeps the plugin's {@code onEnable}
 * free of repetitive {@code registerEvents} calls and gives a single place to unregister everything on shutdown.
 */
public final class ListenerRegistry implements Service {

    private final CorePlugin plugin;

    public ListenerRegistry(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the given listeners unconditionally.
     */
    public void register(Listener... listeners) {
        for (Listener listener : listeners) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    /**
     * Registers only the candidates annotated with {@link AutoRegister}, ignoring the rest.
     */
    public void registerAnnotated(Listener... candidates) {
        for (Listener candidate : candidates) {
            if (candidate.getClass().isAnnotationPresent(AutoRegister.class)) {
                register(candidate);
            }
        }
    }

    /**
     * Unregisters every listener owned by this plugin.
     */
    public void unregisterAll() {
        HandlerList.unregisterAll(plugin);
    }
}
