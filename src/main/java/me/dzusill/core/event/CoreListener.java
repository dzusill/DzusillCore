package me.dzusill.core.event;

import org.bukkit.event.Listener;

import me.dzusill.core.CorePlugin;

/**
 * Base class for event listeners. Provides typed access to the owning plugin and a single place to evolve shared
 * listener behaviour. Registration is handled centrally by the {@link ListenerRegistry}.
 */
public abstract class CoreListener implements Listener {

    protected final CorePlugin plugin;

    protected CoreListener(CorePlugin plugin) {
        this.plugin = plugin;
    }
}
