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
import org.bukkit.plugin.Plugin;
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
        claimNames(bridge, command);
        registered.add(bridge);
        armLabelCapture();
        armFinalClaim();
    }

    /**
     * Takes the names again once the server has finished starting, and rebuilds the command tree.
     *
     * <p>
     * Taking them during {@code onEnable} is not enough, and the reason is only visible from the outside: the server
     * re-synchronises its commands <em>after</em> every plugin has enabled, and that pass puts its own back. Ours
     * survived until the last moment of startup and then quietly lost the name again — which is why the map looked
     * correct from inside {@code register} and wrong to every player who typed the command.
     * </p>
     *
     * <p>
     * {@link ServerLoadEvent} fires after that pass, so this is the first moment the answer sticks. Re-syncing after it
     * is what pushes the new tree to clients; without that the map says one thing and the dispatcher another.
     * </p>
     */
    private void armFinalClaim() {
        if (finalClaimArmed) {
            return;
        }
        finalClaimArmed = true;
        me.dzusill.core.scheduler.SchedulerService scheduler = plugin.services()
                .get(me.dzusill.core.scheduler.SchedulerService.class);
        Bukkit.getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.MONITOR)
            public void onServerLoad(org.bukkit.event.server.ServerLoadEvent event) {
                reclaimAll();
                syncCommands();
            }

            /**
             * Reclaims and resends before this specific player's own tree matters.
             *
             * <p>
             * A one-time claim at startup is not enough against a plugin that takes the name back <em>later</em> - one
             * that finishes its own setup after ours, or reasserts itself on some later hook of its own. Nothing here
             * needs to know what that plugin is doing or when: whatever the state of the shared map is, this puts it
             * right again immediately before the moment it is about to matter to a real player, which is the only
             * moment that was ever actually reported broken.
             * </p>
             *
             * <p>
             * Deferred by a tick, the same fix {@code IndexRefreshListener} elsewhere in this ecosystem uses for the
             * identical reason: the tree Paper sends on login is built before this fires, so resending immediately
             * would resend the same stale one. Scheduled at the joining player, not globally - on Folia a fresh
             * connection is not yet guaranteed to belong to the region {@link #sync} runs on, and
             * {@code player.updateCommands()} needs the thread that owns them.
             * </p>
             */
            @EventHandler(priority = EventPriority.MONITOR)
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                if (scheduler == null) {
                    return;
                }
                org.bukkit.entity.Player player = event.getPlayer();
                scheduler.atEntityLater(player, () -> {
                    if (reclaimAll()) {
                        syncCommands();
                    }
                    player.updateCommands();
                }, 1L);
            }
        }, plugin);
        armSelfHeal(scheduler);
    }

    /**
     * Periodically re-takes any name we are entitled to but no longer hold.
     *
     * <p>
     * The join-time reclaim covers a joining player; this covers everybody already connected when some other plugin
     * takes a name back. Cheap in the steady state - a handful of map lookups - and it is the only defence that does
     * not need to know what raced us or when: whatever took the name, this notices and corrects it within one interval
     * rather than for the rest of the server's life.
     * </p>
     */
    private void armSelfHeal(me.dzusill.core.scheduler.SchedulerService scheduler) {
        if (scheduler == null) {
            return;
        }
        scheduler.repeating(() -> {
            if (reclaimAll()) {
                syncCommands();
                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                    player.updateCommands();
                }
            }
        }, 100L, 100L);
    }

    /** @return whether anything actually needed reclaiming */
    private boolean reclaimAll() {
        boolean changed = false;
        for (Map.Entry<String, BridgeCommand> entry : captured.entrySet()) {
            if (!registered.contains(entry.getValue())) {
                continue;
            }
            changed |= reclaim(entry.getKey(), entry.getValue());
        }
        return changed;
    }

    /**
     * Takes one label back, if we are allowed to hold it and something else has it.
     *
     * @return whether the label was not already ours - i.e. whether this call actually changed anything
     */
    private boolean reclaim(String label, BridgeCommand bridge) {
        Map<String, Command> known = knownCommands();
        if (known == null) {
            return false;
        }
        Command holder = known.get(label);
        if (holder == bridge) {
            return false;
        }
        if (!mayAnswer(label)) {
            return false;
        }
        if (holder != null) {
            holder.unregister(commandMap);
            known.remove(label);
        }
        known.put(label, bridge);
        if (!claimed.contains(label)) {
            claimed.add(label);
        }
        return true;
    }

    /**
     * Rebuilds the server's command tree from the command map and sends it to everybody online.
     *
     * <p>
     * Reflective because it is a CraftBukkit method with no API equivalent. Failing is survivable: the commands still
     * run, and the tree corrects itself the next time the server syncs on its own — a plugin reload, or the next
     * restart.
     * </p>
     */
    private void syncCommands() {
        try {
            java.lang.reflect.Method sync = plugin.getServer().getClass().getMethod("syncCommands");
            sync.setAccessible(true);
            sync.invoke(plugin.getServer());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notAvailable) {
            plugin.getLogger().fine("Could not rebuild the command tree: " + notAvailable);
        }
    }

    /**
     * Puts this command under its plain name in the command map, displacing whatever held it.
     *
     * <p>
     * This is the difference between running under a name and <em>owning</em> it, and only the second one reaches a
     * player's keyboard. The client builds its command tree from what the server sends, the server filters that list by
     * permission, and a client only asks for completions on a node it was given. Registered as {@code oberonstaff:tp}
     * alone, our {@code /tp} is not on the plain name at all: the node a player sees there is vanilla's, gated on
     * operator. So an admin got suggestions — vanilla's, not ours — and everybody else got silence, which reads exactly
     * like a broken plugin and cannot be fixed anywhere on the server side, because no packet is ever sent to answer.
     * </p>
     *
     * <p>
     * Taking the map entry puts our node on the plain name with <em>our</em> permission, and Paper rebuilds the
     * dispatcher from the command map once plugins have enabled. The rule for what may be taken is unchanged: names the
     * server owns, and names another plugin owns only when the config asked for it.
     * </p>
     *
     * <p>
     * Best effort by design. {@code getKnownCommands} is Paper-only and this framework compiles against Spigot, so on
     * anything that does not expose it the label rewrite below still carries execution — the same behaviour as before
     * this existed, never worse.
     * </p>
     */
    private void claimNames(BridgeCommand bridge, CoreCommand command) {
        Map<String, Command> known = knownCommands();
        if (known == null) {
            // Worth saying once: without this, tab completion on a name the server owns cannot work for anybody who
            // is not an operator, and the symptom gives no hint why.
            if (!warnedAboutCommandMap) {
                warnedAboutCommandMap = true;
                plugin.getLogger().warning("Cannot read the server's command map, so command names cannot be taken"
                        + " from the server. Commands still run, but tab completion for a name the server owns will"
                        + " only work for operators.");
            }
            return;
        }
        List<String> labels = new java.util.ArrayList<>();
        labels.add(command.name().toLowerCase(Locale.ROOT));
        for (String alias : command.aliases()) {
            labels.add(alias.toLowerCase(Locale.ROOT));
        }
        for (String label : labels) {
            Command holder = known.get(label);
            if (holder == bridge || !mayAnswer(label)) {
                // Already ours, or a name another plugin owns and we were not told to take.
                continue;
            }
            if (holder != null) {
                // unregister() alone leaves the entry behind on some versions, so the label goes too.
                holder.unregister(commandMap);
                known.remove(label);
                plugin.getLogger().info("Took /" + label + " from " + describe(holder) + ".");
            }
            known.put(label, bridge);
            if (!claimed.contains(label)) {
                claimed.add(label);
            }
        }
    }

    /** What held a name before we took it, for the line that says so. */
    private static String describe(Command holder) {
        if (holder instanceof PluginIdentifiableCommand identifiable) {
            try {
                Plugin owner = identifiable.getPlugin();
                if (owner != null && Bukkit.getPluginManager().getPlugin(owner.getName()) == owner) {
                    return owner.getName();
                }
            } catch (RuntimeException internal) {
                // An internal wrapper that will not answer. It is the server's either way.
            }
        }
        return "the server's built-in command";
    }

    /** @return the names taken from the server or another plugin, for the startup report */
    public List<String> claimed() {
        return List.copyOf(claimed);
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
     * @return one line per registered label, e.g. {@code /tphere -> SomeTpaPlugin}. When we own the label, the line
     *         also names the permission the live {@link Command} object itself requires — not what a config file says
     *         it should be, but what {@code testPermissionSilent} actually checks. A plugin that inspects the command
     *         map directly (a whitelist deciding what to suggest, say) uses this value, not ours; the two have no
     *         reason to differ, but a report that only assumes they match cannot catch it when they do not.
     */
    public List<String> ownershipReport() {
        List<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<String, BridgeCommand> entry : new java.util.TreeMap<>(captured).entrySet()) {
            Command owner = commandMap.getCommand(entry.getKey());
            String who;
            if (owner == entry.getValue() || owner == null) {
                who = plugin.getName();
                String permission = entry.getValue().getPermission();
                who += " (permission: " + (permission == null || permission.isEmpty() ? "none" : permission) + ")";
            } else if (ownedByAnotherPlugin(entry.getKey())) {
                who = ((PluginIdentifiableCommand) owner).getPlugin().getName()
                        + (forced.contains(entry.getKey()) ? " (taken on use)" : "");
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
            if (owner != entry.getValue() && !forced.contains(entry.getKey()) && ownedByAnotherPlugin(entry.getKey())) {
                lines.add("/" + entry.getKey() + " is owned by "
                        + ((PluginIdentifiableCommand) owner).getPlugin().getName());
            }
        }
        return lines;
    }

    /**
     * Whether this registry registered the label and answers for it.
     *
     * <p>
     * Exists for plugins that inspect commands before they run — a whitelist, an audit log, a cooldown layer. Such a
     * plugin sees the message <em>after</em> {@link #armLabelCapture()} may have rewritten it into the namespaced form,
     * and without a way to ask, it cannot tell that rewrite apart from a player typing {@code /plugin:name} to slip
     * past a list. The answer is the same ownership rule the execute and completion paths use, so all three agree by
     * construction.
     * </p>
     *
     * @param label
     *            a command label, with or without a leading slash, namespace or arguments
     * @return whether the label resolves to a command this registry owns
     */
    public boolean owns(String label) {
        if (label == null) {
            return false;
        }
        String name = label.trim();
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        int space = name.indexOf(' ');
        if (space >= 0) {
            name = name.substring(0, space);
        }
        int colon = name.lastIndexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        name = name.toLowerCase(Locale.ROOT);
        return captured.containsKey(name) && mayAnswer(name);
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
        return !ownedByAnotherPlugin(label);
    }

    /**
     * Whether a real third-party plugin owns this name.
     *
     * <p>
     * The distinction that matters is between another <em>plugin</em> and the <em>server</em>. A name another plugin
     * owns is left alone, because two plugins claiming one name is a server configuration to resolve rather than
     * something to settle here by load order. A name the server owns is ours to take — that is the entire point of the
     * capture, and vanilla {@code /tp} logs nothing and respects no {@code /tptoggle}.
     * </p>
     *
     * <p>
     * Asking {@code instanceof PluginIdentifiableCommand} alone used to be enough, back when vanilla commands were
     * plain wrappers. Newer Paper hands them out owned by an internal plugin, which made every vanilla name look like a
     * third party's — and the consequence was invisible in the worst way. Execution still reached us through the label
     * rewrite, but completion declined, Paper fell through to the vanilla Brigadier node, and that node requires
     * operator. So {@code /tp } suggested players for an admin and nothing at all for the staff the command was granted
     * to, which reads exactly like a broken plugin and not like a version change.
     * </p>
     *
     * <p>
     * A plugin the plugin manager does not know is not a plugin: internal ones are never registered with it. That test
     * needs no version-specific class name and no list of the server's own plugin names to keep up to date.
     * </p>
     */
    private boolean ownedByAnotherPlugin(String label) {
        Command owner = commandMap.getCommand(label);
        if (!(owner instanceof PluginIdentifiableCommand identifiable)) {
            return false;
        }
        Plugin other;
        try {
            other = identifiable.getPlugin();
        } catch (RuntimeException internalCommand) {
            // Some internal wrappers throw rather than answer. That is the server's, not a plugin's.
            return false;
        }
        if (other == null || other.equals(plugin)) {
            return false;
        }
        return Bukkit.getPluginManager().getPlugin(other.getName()) == other;
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

    /** Labels this registry took over in the command map, in the order it took them. */
    private final List<String> claimed = new java.util.ArrayList<>();

    /** The bridges this registry created, so the final claim only re-takes names it is entitled to. */
    private final Set<BridgeCommand> registered = new java.util.HashSet<>();

    private boolean captureArmed;
    private boolean finalClaimArmed;
    private boolean warnedAboutCommandMap;

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
