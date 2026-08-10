package me.dzusill.core.command;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.service.Service;

/**
 * Registers {@link CoreCommand}s with the server at runtime through Bukkit's {@link CommandMap}, so commands never need
 * to be declared in {@code plugin.yml}. Each command is injected with its plugin and {@link MessageService}, then
 * wrapped in a lightweight {@link Command} bridge that delegates execution and tab-completion back to the framework's
 * dispatch.
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
     * {@code Server#getCommandMap()} is a Paper-only convenience method, not part of plain Spigot/CraftBukkit's public
     * API. The private {@code commandMap} field on the server implementation class has been stable since early Bukkit,
     * so reflection works identically on every supported server implementation.
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
        BridgeCommand bridge = new BridgeCommand(command);
        commandMap.register(fallbackPrefix, bridge);
        captured.add(command.name().toLowerCase(Locale.ROOT));
        for (String alias : command.aliases()) {
            captured.add(alias.toLowerCase(Locale.ROOT));
        }
        armLabelCapture();
    }

    /**
     * Makes sure a name we registered reaches us and not the server's built-in command of the same name.
     *
     * <p>
     * Minecraft ships {@code /msg} (aliases {@code /tell}, {@code /w}), {@code /tp} and others. Registering a plugin
     * command with the same name is not enough: our command does land in the {@link CommandMap}, but Paper dispatches
     * through Brigadier, where the vanilla node still sits on that name and answers first. Ours stays reachable only as
     * {@code /plugin:name}. It fails silently, which is the dangerous part - {@code /msg} looks like it works while
     * being the vanilla one, which no plugin logs, no ignore list applies to, and no chat filter sees.
     * </p>
     *
     * <p>
     * Removing the Brigadier node would mean reflecting into the dispatcher's internals, which change shape between
     * Minecraft versions. Rewriting the label before dispatch does the same job using only public events, and keeps
     * working on versions that did not exist when this was written.
     * </p>
     *
     * <p>
     * Only labels this framework registered are rewritten, and only when nothing else on the server owns them as a
     * plugin command - two plugins claiming one name stays a server configuration to resolve, not something decided
     * here by load order.
     * </p>
     */
    private void armLabelCapture() {
        if (captureArmed) {
            return;
        }
        captureArmed = true;
        Bukkit.getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
                String rewritten = rewrite(event.getMessage().substring(1));
                if (rewritten != null) {
                    event.setMessage("/" + rewritten);
                }
            }

            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            public void onConsoleCommand(ServerCommandEvent event) {
                String rewritten = rewrite(event.getCommand());
                if (rewritten != null) {
                    event.setCommand(rewritten);
                }
            }
        }, plugin);
    }

    /**
     * @return {@code commandLine} with its label namespaced to this plugin, or {@code null} to leave it alone
     */
    private String rewrite(String commandLine) {
        int space = commandLine.indexOf(' ');
        String label = (space < 0 ? commandLine : commandLine.substring(0, space)).toLowerCase(Locale.ROOT);
        if (label.indexOf(':') >= 0 || !captured.contains(label)) {
            return null;
        }
        Command owner = commandMap.getCommand(label);
        if (owner instanceof PluginIdentifiableCommand) {
            // Another plugin holds this name outright. Not ours to take.
            return null;
        }
        return fallbackPrefix + ":" + commandLine;
    }

    private final Set<String> captured = new java.util.HashSet<>();
    private boolean captureArmed;

    /**
     * The command map's backing map, or {@code null} when it cannot be reached.
     *
     * <p>
     * {@code unregister} alone leaves the entry in place on some versions, so the label is removed directly as well.
     * Reflective because {@code getKnownCommands} is Paper-only and this framework compiles against Spigot.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands() {
        try {
            java.lang.reflect.Method accessor = commandMap.getClass().getMethod("getKnownCommands");
            return (Map<String, Command>) accessor.invoke(commandMap);
        } catch (ReflectiveOperationException | RuntimeException noAccessor) {
            try {
                java.lang.reflect.Field field = commandMap.getClass().getDeclaredField("knownCommands");
                field.setAccessible(true);
                return (Map<String, Command>) field.get(commandMap);
            } catch (ReflectiveOperationException | RuntimeException unreachable) {
                return null;
            }
        }
    }

    /**
     * Adapts a framework {@link CoreCommand} to Bukkit's {@link Command} type required by the {@link CommandMap}.
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
