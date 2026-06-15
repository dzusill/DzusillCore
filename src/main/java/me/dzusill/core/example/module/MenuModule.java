package me.dzusill.core.example.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.event.ListenerRegistry;
import me.dzusill.core.example.listener.PlayerSessionListener;
import me.dzusill.core.menu.MenuListener;
import me.dzusill.core.menu.MenuManager;
import me.dzusill.core.module.AbstractModule;

/**
 * Sets up the GUI subsystem: publishes the {@link MenuManager}, registers the central
 * {@link MenuListener}, and demonstrates annotation-based listener registration. Closes all open
 * menus on shutdown.
 */
public final class MenuModule extends AbstractModule {

    private MenuManager menuManager;

    public MenuModule(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Menus";
    }

    @Override
    public void onEnable() {
        this.menuManager = new MenuManager();
        provide(MenuManager.class, menuManager);

        ListenerRegistry listeners = service(ListenerRegistry.class);
        listeners.register(new MenuListener(plugin));
        listeners.registerAnnotated(new PlayerSessionListener(plugin, menuManager));
    }

    @Override
    public void onDisable() {
        if (menuManager != null) {
            menuManager.closeAll();
        }
    }
}
