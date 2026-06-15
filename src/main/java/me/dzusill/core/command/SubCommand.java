package me.dzusill.core.command;

import me.dzusill.core.command.argument.ArgumentParser;
import me.dzusill.core.command.argument.ArgumentType;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;
import me.dzusill.core.util.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A single node in a command tree. A node can either route to child nodes (acting as a group such
 * as {@code /core ...}) or be a leaf that declares typed arguments and does work in {@link #run}.
 * The same class powers both, so the whole tree is handled polymorphically: permission and
 * player-only checks, argument parsing and tab-completion all live here and are inherited by every
 * concrete command.
 *
 * <p>Configure a node from its subclass constructor using {@link #permission(String)},
 * {@link #playerOnly()}, {@link #arg}, {@link #optionalArg} and {@link #child(SubCommand)}, or
 * annotate the class with {@link CommandMeta} and use the no-arg constructor.</p>
 */
public abstract class SubCommand {

    private String name;
    private String description = "";
    private String permission = "";
    private boolean playerOnly = false;
    private final List<String> aliases = new ArrayList<>();
    private final Map<String, SubCommand> children = new LinkedHashMap<>();
    private final List<ArgumentParser.Spec> argSpecs = new ArrayList<>();

    /**
     * Configures the node from a {@link CommandMeta} annotation on the concrete class.
     *
     * @throws IllegalStateException if the class is not annotated
     */
    protected SubCommand() {
        CommandMeta meta = getClass().getAnnotation(CommandMeta.class);
        if (meta == null) {
            throw new IllegalStateException(getClass().getName()
                    + " must be annotated with @CommandMeta or call super(name)");
        }
        this.name = meta.name().toLowerCase(Locale.ROOT);
        this.description = meta.description();
        this.permission = meta.permission();
        this.playerOnly = meta.playerOnly();
        for (String alias : meta.aliases()) {
            this.aliases.add(alias.toLowerCase(Locale.ROOT));
        }
    }

    protected SubCommand(String name) {
        this.name = name.toLowerCase(Locale.ROOT);
    }

    // --- configuration (call from subclass constructor) ---------------------

    protected SubCommand permission(String permission) {
        this.permission = permission;
        return this;
    }

    protected SubCommand description(String description) {
        this.description = description;
        return this;
    }

    protected SubCommand playerOnly() {
        this.playerOnly = true;
        return this;
    }

    protected SubCommand alias(String... aliases) {
        for (String alias : aliases) {
            this.aliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    /**
     * Declares a required argument at the next position.
     */
    protected <T> SubCommand arg(String argName, ArgumentType<T> type) {
        argSpecs.add(new ArgumentParser.Spec(argName, type, true));
        return this;
    }

    /**
     * Declares an optional argument at the next position.
     */
    protected <T> SubCommand optionalArg(String argName, ArgumentType<T> type) {
        argSpecs.add(new ArgumentParser.Spec(argName, type, false));
        return this;
    }

    /**
     * Registers a child node under its name and aliases.
     */
    protected SubCommand child(SubCommand child) {
        children.put(child.name, child);
        for (String alias : child.aliases) {
            children.put(alias, child);
        }
        return this;
    }

    // --- execution ----------------------------------------------------------

    /**
     * Runs this node's logic. For pure routing nodes, override to show help; for leaf nodes,
     * implement the actual behaviour using the already-parsed {@code args}.
     *
     * @param offsetArgs arguments parsed from this node's declared specs
     */
    public abstract void run(CommandContext context, Arguments offsetArgs) throws CommandException;

    /**
     * Validates access, routes to a child if one matches, otherwise parses this node's arguments
     * and invokes {@link #run}.
     *
     * @param offset index of the first token belonging to this node
     */
    final void execute(CommandContext context, int offset) throws CommandException {
        if (!permission.isEmpty() && !context.sender().hasPermission(permission)) {
            throw new CommandException(Messages.NO_PERMISSION);
        }
        if (playerOnly && !context.isPlayer()) {
            throw new CommandException(Messages.PLAYERS_ONLY);
        }

        if (!children.isEmpty() && offset < context.size()) {
            SubCommand child = children.get(context.arg(offset).toLowerCase(Locale.ROOT));
            if (child != null) {
                child.execute(context, offset + 1);
                return;
            }
        }

        Arguments parsed = new ArgumentParser(argSpecs).parse(context, context.args(), offset);
        run(context, parsed);
    }

    /**
     * Produces tab-completion suggestions for the token currently being typed.
     */
    final List<String> complete(CommandContext context, int offset) {
        if (!permission.isEmpty() && !context.sender().hasPermission(permission)) {
            return List.of();
        }

        if (!children.isEmpty()) {
            int remaining = context.size() - offset;
            if (remaining <= 1) {
                String token = offset < context.size() ? context.arg(offset) : "";
                return TextUtils.partialMatches(token, visibleChildNames(context));
            }
            SubCommand child = children.get(context.arg(offset).toLowerCase(Locale.ROOT));
            return child != null ? child.complete(context, offset + 1) : List.of();
        }

        return new ArgumentParser(argSpecs).suggest(context, context.args(), offset);
    }

    private List<String> visibleChildNames(CommandContext context) {
        List<String> names = new ArrayList<>();
        for (SubCommand child : toPrimaryChildren().values()) {
            if (child.permission.isEmpty() || context.sender().hasPermission(child.permission)) {
                names.add(child.name);
            }
        }
        return names;
    }

    /**
     * @return children keyed only by their primary name (de-duplicates alias entries)
     */
    private Map<String, SubCommand> toPrimaryChildren() {
        Map<String, SubCommand> primary = new LinkedHashMap<>();
        for (SubCommand child : children.values()) {
            primary.putIfAbsent(child.name, child);
        }
        return primary;
    }

    /**
     * @return a usage fragment for this node ({@code <a|b>} for routers, {@code <arg> [opt]} for leaves)
     */
    public String usage() {
        if (!children.isEmpty()) {
            return "<" + String.join("|", toPrimaryChildren().keySet()) + ">";
        }
        return new ArgumentParser(argSpecs).usage();
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return permission;
    }

    public List<String> aliases() {
        return List.copyOf(aliases);
    }

    /**
     * @return primary-name view of this node's children, for help rendering
     */
    protected Map<String, SubCommand> children() {
        return toPrimaryChildren();
    }
}
