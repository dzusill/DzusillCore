package me.dzusill.core.command;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Root of a command tree and the bridge to Bukkit. A {@code CoreCommand} <em>is</em> a
 * {@link SubCommand} (the root node), but additionally implements {@link CommandExecutor} and
 * {@link TabCompleter} so it can be registered with the server. All dispatch, permission checks,
 * argument parsing and tab-completion are inherited from {@link SubCommand}; subclasses only
 * declare structure and implement {@link #run}.
 *
 * <p>Register instances through {@link CommandRegistry}; do not declare them in {@code plugin.yml}.</p>
 */
public abstract class CoreCommand extends SubCommand implements CommandExecutor, TabCompleter {

    private CorePlugin plugin;
    private MessageService messages;

    protected CoreCommand() {
        super();
    }

    protected CoreCommand(String name) {
        super(name);
    }

    /**
     * Injected by {@link CommandRegistry} before registration.
     */
    void init(CorePlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public final boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                   @NotNull String label, @NotNull String[] args) {
        CommandContext context = new CommandContext(plugin, sender, messages, label, args);
        try {
            execute(context, 0);
        } catch (CommandException ex) {
            messages.send(sender, ex.messageKey(), ex.placeholder());
        }
        return true;
    }

    @Override
    public final List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                            @NotNull String alias, @NotNull String[] args) {
        CommandContext context = new CommandContext(plugin, sender, messages, alias, args);
        return complete(context, 0);
    }
}
