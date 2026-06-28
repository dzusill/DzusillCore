package me.dzusill.core.example.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.event.ListenerRegistry;
import me.dzusill.core.example.menu.ShopMenu;
import me.dzusill.core.menu.MenuListener;
import me.dzusill.core.menu.MenuManager;
import me.dzusill.core.menu.MenuRegistry;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.module.AbstractModule;

/**
 * Sets up the GUI subsystem: publishes the {@link MenuManager} and the {@link MenuRegistry}, registers the central
 * {@link MenuListener}, and registers menus by key (just as {@link CommandModule} registers commands). Closes all open
 * menus on shutdown. Per-player context cleanup on quit is owned by the {@link MenuManager} itself (it self-registers
 * the quit listener), so no plugin-level quit handling is needed.
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
        this.menuManager = new MenuManager(plugin);
        provide(MenuManager.class, menuManager);

        MenuRegistry menus = new MenuRegistry(plugin, menuManager, service(MessageService.class));
        provide(MenuRegistry.class, menus);
        menus.register("shop", ShopMenu::new);

        ListenerRegistry listeners = service(ListenerRegistry.class);
        listeners.register(new MenuListener(plugin));
    }

    @Override
    public void onDisable() {
        if (menuManager != null) {
            menuManager.closeAll();
        }
    }
}
