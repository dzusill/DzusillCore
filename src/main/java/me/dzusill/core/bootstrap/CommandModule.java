package me.dzusill.core.bootstrap;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.command.CommandRegistry;
import me.dzusill.core.command.CoreAdminCommand;
import me.dzusill.core.config.ConfigManager;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.module.AbstractModule;

/**
 * Provides the {@link CommandRegistry} and registers the framework's own administration command.
 *
 * <p>
 * {@code /core reload} is the only command the framework claims. The demo {@code /shop}, {@code /heal} and
 * {@code /coredialog} that used to live here are gone: they documented the framework by putting themselves in the
 * command map of every server that installed it, and {@code /shop} defaulted to everyone.
 * </p>
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
    }
}
