package me.dzusill.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.message.Placeholder;

/**
 * Per-invocation context handed to command nodes. Bundles the sender, the raw arguments and the label, plus convenience
 * accessors and a shortcut for replying through the {@link MessageService}.
 */
public final class CommandContext {

    private final CorePlugin plugin;
    private final CommandSender sender;
    private final MessageService messages;
    private final String label;
    private final String[] args;

    public CommandContext(CorePlugin plugin, CommandSender sender, MessageService messages, String label,
            String[] args) {
        this.plugin = plugin;
        this.sender = sender;
        this.messages = messages;
        this.label = plain(label);
        this.args = args;
    }

    /**
     * The command as the sender typed it, without the namespace the framework may have added on the way in.
     *
     * <p>
     * A name the server owns is reached by rewriting {@code /msg hi} to {@code /oberonmsg:msg hi} before dispatch, so
     * the label Bukkit hands back is the internal one. Echoing that into a message told a player their mistake was
     * {@code Usage: /oberonmsg:msg <player>} — a command they never typed and could not have known about.
     * </p>
     */
    private static String plain(String label) {
        if (label == null) {
            return "";
        }
        int namespace = label.indexOf(':');
        return namespace < 0 ? label : label.substring(namespace + 1);
    }

    public CorePlugin plugin() {
        return plugin;
    }

    public CommandSender sender() {
        return sender;
    }

    public MessageService messages() {
        return messages;
    }

    public String label() {
        return label;
    }

    public String[] args() {
        return args;
    }

    public int size() {
        return args.length;
    }

    /**
     * @return the argument at {@code index}, or an empty string if out of bounds
     */
    public String arg(int index) {
        return index >= 0 && index < args.length ? args[index] : "";
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * @return the sender cast to {@link Player}; only call after checking {@link #isPlayer()} or when the command is
     *         declared player-only
     */
    public Player player() {
        return (Player) sender;
    }

    /**
     * Replies to the sender with a configured message.
     */
    public void reply(String messageKey, Placeholder placeholder) {
        messages.send(sender, messageKey, placeholder);
    }

    public void reply(String messageKey) {
        messages.send(sender, messageKey);
    }
}
