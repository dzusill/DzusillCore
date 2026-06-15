package me.dzusill.core.example.command;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CoreCommand;
import me.dzusill.core.command.SubCommand;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;
import me.dzusill.core.config.ConfigManager;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;

/**
 * Example router command demonstrating a command tree: {@code /core} routes to subcommands and,
 * when invoked bare, prints its usage. Shows how a node with children inherits routing and
 * permission handling from {@link SubCommand}.
 */
@CommandMeta(name = "core", permission = "core.admin", description = "DzusillCore administration")
public final class CoreAdminCommand extends CoreCommand {

    public CoreAdminCommand(ConfigManager configs, MessageService messages) {
        super();
        child(new ReloadSubCommand(configs, messages));
    }

    @Override
    public void run(CommandContext context, Arguments args) {
        context.reply(Messages.INVALID_USAGE, Placeholder.of("usage", "/" + context.label() + " " + usage()));
    }

    /**
     * Reloads all configs and messages.
     */
    @CommandMeta(name = "reload", permission = "core.reload", description = "Reload configuration")
    private static final class ReloadSubCommand extends SubCommand {

        private final ConfigManager configs;
        private final MessageService messages;

        private ReloadSubCommand(ConfigManager configs, MessageService messages) {
            super();
            this.configs = configs;
            this.messages = messages;
        }

        @Override
        public void run(CommandContext context, Arguments args) {
            try {
                configs.reload();
                messages.reload();
                context.reply(Messages.RELOAD_SUCCESS);
            } catch (Exception ex) {
                context.reply(Messages.RELOAD_FAILED);
            }
        }
    }
}
