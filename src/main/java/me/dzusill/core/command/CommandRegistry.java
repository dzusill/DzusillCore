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
import org.bukkit.event.server.TabCompleteEvent;
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
        captured.put(command.name().toLowerCase(Locale.ROOT), bridge);
        for (String alias : command.aliases()) {
            captured.put(alias.toLowerCase(Locale.ROOT), bridge);
        }
        if (command.takeNameFromOtherPlugins()) {
            forced.add(command.name().toLowerCase(Locale.ROOT));
            for (String alias : command.aliases()) {
                forced.add(alias.toLowerCase(Locale.ROOT));
            }
        }
        armLabelCapture();
    }

    /**
     * Reports, for every name this registry claimed, who the server will actually hand it to.
     *
     * <p>
     * Exists because the failure it describes is invisible. A command whose name another plugin owns still answers when
     * typed - the label is rewritten below - so nothing looks wrong until somebody notices it does not tab-complete, or
     * that the other plugin's version is the one running. Printing the answer turns that into something a server owner
     * can read at startup.
     * </p>
     *
     * @return one line per registered label, e.g. {@code /tphere -> SomeTpaPlugin}
     */
    public List<String> ownershipReport() {
        List<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, BridgeCommand> entry : new java.util.TreeMap<>(captured).entrySet()) {
            Command owner = commandMap.getCommand(entry.getKey());
            String who;
            if (owner == entry.getValue()) {
                who = plugin.getName();
            } else if (owner instanceof PluginIdentifiableCommand identifiable) {
                who = identifiable.getPlugin().getName() + (forced.contains(entry.getKey()) ? " (taken on use)" : "");
            } else if (owner == null) {
                who = plugin.getName();
            } else {
                who = "the server (taken on use)";
            }
            lines.add("/" + entry.getKey() + " -> " + who);
        }
        return lines;
    }

    /** @return the labels another plugin owns and we are not configured to take */
    public List<String> conflicts() {
        List<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, BridgeCommand> entry : new java.util.TreeMap<>(captured).entrySet()) {
            Command owner = commandMap.getCommand(entry.getKey());
            if (owner != entry.getValue() && owner instanceof PluginIdentifiableCommand identifiable
                    && !forced.contains(entry.getKey())) {
                lines.add("/" + entry.getKey() + " is owned by " + identifiable.getPlugin().getName());
            }
        }
        return lines;
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

            @EventHandler(priority = EventPriority.HIGH)
            public void onTabComplete(TabCompleteEvent event) {
                List<String> ours = completionsFor(event.getSender(), event.getBuffer());
                if (ours != null) {
                    event.setCompletions(ours);
                }
            }
        }, plugin);

        // On Paper, a player's completion never reaches the event above for a name somebody else owns in Brigadier
        // — which is the only case that needs help. See PaperTabCompleteBridge.
        PaperTabCompleteBridge.register(plugin, this::completionsFor);
    }

    /**
     * Completions for a buffer whose command we answer, or {@code null} when it is none of our business.
     *
     * <p>
     * Rewriting the label on execution is not enough on its own. Tab completion never passes through
     * {@code PlayerCommandPreprocessEvent}, so a name owned by vanilla or another plugin ends up <em>running</em> as
     * ours while <em>completing</em> as theirs - the command works when typed in full and suggests nothing, which is
     * exactly the shape this was reported in. This closes that gap on the same ownership rule.
     * </p>
     */
    private List<String> completionsFor(CommandSender sender, String buffer) {
        if (buffer == null || !buffer.startsWith("/")) {
            // Chat completion, not a command.
            return null;
        }
        String line = buffer.substring(1);
        int space = line.indexOf(' ');
        if (space < 0) {
            // Still typing the command name itself; the server's own list is the right answer.
            return null;
        }
        String label = line.substring(0, space).toLowerCase(Locale.ROOT);
        if (label.indexOf(':') >= 0) {
            label = label.substring(label.indexOf(':') + 1);
        }
        BridgeCommand ours = captured.get(label);
        if (ours == null || !mayAnswer(label)) {
            return null;
        }
        String[] args = line.substring(space + 1).split(" ", -1);
        return ours.tabComplete(sender, label, args);
    }

    /** @return whether the label is one we are allowed to answer for — the same rule the execute path uses */
    private boolean mayAnswer(String label) {
        if (forced.contains(label)) {
            return true;
        }
        Command owner = commandMap.getCommand(label);
        return !(owner instanceof PluginIdentifiableCommand identifiable) || identifiable.getPlugin().equals(plugin);
    }

    /**
     * @return {@code commandLine} with its label namespaced to this plugin, or {@code null} to leave it alone
     */
    private String rewrite(String commandLine) {
        int space = commandLine.indexOf(' ');
        String label = (space < 0 ? commandLine : commandLine.substring(0, space)).toLowerCase(Locale.ROOT);
        if (label.indexOf(':') >= 0 || !captured.containsKey(label) || !mayAnswer(label)) {
            return null;
        }
        return fallbackPrefix + ":" + commandLine;
    }

    /** Every label we registered, mapped to the command that answers it. */
    private final Map<String, BridgeCommand> captured = new java.util.HashMap<>();

    /**
     * Labels the owning plugin asked us to take even from another plugin.
     *
     * <p>
     * Off unless asked for. Two plugins claiming one name is a server configuration to resolve, not something to settle
     * here by load order - but a server owner who knows their teleport plugin only uses {@code /tphere} as an alias
     * needs a way to say so.
     * </p>
     */
    private final Set<String> forced = new java.util.HashSet<>();

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
