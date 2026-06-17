package me.dzusill.core.example.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.command.CommandRegistry;
import me.dzusill.core.config.ConfigManager;
import me.dzusill.core.example.command.CoreAdminCommand;
import me.dzusill.core.example.command.HealCommand;
import me.dzusill.core.example.command.ShopCommand;
import me.dzusill.core.menu.MenuRegistry;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.module.AbstractModule;

/**
 * Registers the example commands through the {@link CommandRegistry}. Demonstrates resolving
 * dependencies (message, config and menu services) from the registry rather than constructing
 * them directly, which is why this module is enabled after the ones that provide them.
 */
public final class CommandModule extends AbstractModule {

    public CommandModule(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Commands";
    }

    @Override
    public void onEnable() {
        MessageService messages = service(MessageService.class);
        CommandRegistry commands = new CommandRegistry(plugin, messages);
        provide(CommandRegistry.class, commands);

        commands.register(new CoreAdminCommand(service(ConfigManager.class), messages));
        commands.register(new HealCommand());
        commands.register(new ShopCommand(service(MenuRegistry.class)));
    }
}
