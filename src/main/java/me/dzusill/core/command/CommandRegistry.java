package me.dzusill.core.command;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.service.Service;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * Registers {@link CoreCommand}s with the server at runtime through Bukkit's {@link CommandMap},
 * so commands never need to be declared in {@code plugin.yml}. Each command is injected with its
 * plugin and {@link MessageService}, then wrapped in a lightweight {@link Command} bridge that
 * delegates execution and tab-completion back to the framework's dispatch.
 */
public final class CommandRegistry implements Service {

    private final CorePlugin plugin;
    private final MessageService messages;
    private final CommandMap commandMap;
    private final String fallbackPrefix;

    public CommandRegistry(CorePlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.commandMap = resolveCommandMap(plugin);
        this.fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code Server#getCommandMap()} is a Paper-only convenience method, not part of plain
     * Spigot/CraftBukkit's public API. The private {@code commandMap} field on the server
     * implementation class has been stable since early Bukkit, so reflection works identically
     * on every supported server implementation.
     */
    private static CommandMap resolveCommandMap(CorePlugin plugin) {
        try {
            Field field = plugin.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(plugin.getServer());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve the server's CommandMap", e);
        }
    }

    /**
     * Wires up and registers a command tree with the server.
     */
    public void register(CoreCommand command) {
        command.init(plugin, messages);
        commandMap.register(fallbackPrefix, new BridgeCommand(command));
    }

    /**
     * Adapts a framework {@link CoreCommand} to Bukkit's {@link Command} type required by the
     * {@link CommandMap}.
     */
    private static final class BridgeCommand extends Command {

        private final CoreCommand handler;

        private BridgeCommand(CoreCommand handler) {
            super(handler.name(), handler.description(), "/" + handler.name(), handler.aliases());
            this.handler = handler;
            if (!handler.permission().isEmpty()) {
                setPermission(handler.permission());
            }
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            return handler.onCommand(sender, this, label, args);
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                                  @NotNull String[] args) {
            List<String> result = handler.onTabComplete(sender, this, alias, args);
            return result != null ? result : List.of();
        }
    }
}
