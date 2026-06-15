package me.dzusill.core.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.service.Service;
import me.dzusill.core.service.ServiceRegistry;

/**
 * Convenience base class for {@link CoreModule} implementations. Provides typed access to the
 * owning {@link CorePlugin} and shortcuts for publishing and resolving services, so concrete
 * modules only implement the behaviour that is unique to them.
 */
public abstract class AbstractModule implements CoreModule {

    protected final CorePlugin plugin;

    protected AbstractModule(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Publishes a service owned by this module into the shared registry.
     */
    protected <T extends Service> void provide(Class<T> type, T service) {
        plugin.services().register(type, service);
    }

    /**
     * Resolves a required service published by another (already enabled) module.
     */
    protected <T extends Service> T service(Class<T> type) {
        return plugin.services().get(type);
    }

    protected ServiceRegistry services() {
        return plugin.services();
    }

    @Override
    public void onDisable() {
        // No-op by default; modules override when they hold resources to release.
    }
}
