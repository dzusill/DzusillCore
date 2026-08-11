package me.dzusill.core.command;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Answers a player's tab completion on Paper, for a command name we claimed but do not own in Brigadier.
 *
 * <p>
 * Bukkit's {@code TabCompleteEvent} is not enough, and reading the server settles it. A player's completion packet is
 * handled by {@code ServerGamePacketListenerImpl.handleCustomCommandSuggestions0}, which fires Paper's
 * {@code AsyncTabCompleteEvent} and nothing else — that class contains no reference to the Bukkit event at all. The
 * Bukkit event is fired one layer down, by {@code BukkitCommandNode$BukkitBrigSuggestionProvider}, which is reached
 * only when the Brigadier node being completed is already <em>our</em> command. That is exactly the case that never
 * needed help.
 * </p>
 *
 * <p>
 * So for the case this exists for — a name vanilla or another plugin owns, which we take on execution — Paper's event
 * is the only one that ever fires. Without this, such a command runs as ours and completes as theirs.
 * </p>
 *
 * <p>
 * Wired reflectively rather than by compiling against Paper. The framework targets the Spigot API surface on purpose,
 * and putting paper-api on the main classpath changes what other code sees — Paper's {@code CommandSender} already
 * implements {@code Audience}, which turns a deliberate {@code instanceof} check in the message layer into an
 * unconditional pattern and stops the build. Reflection keeps that boundary intact and costs one lookup at startup.
 * </p>
 */
final class PaperTabCompleteBridge {

    private static final String EVENT = "com.destroystokyo.paper.event.server.AsyncTabCompleteEvent";

    private PaperTabCompleteBridge() {
    }

    /**
     * Registers the bridge when the server is Paper.
     *
     * @param completions
     *            {@code (sender, buffer) -> completions}, or {@code null} to leave the buffer alone
     * @return whether it was registered; {@code false} on plain Spigot, where the Bukkit event is the right one
     */
    @SuppressWarnings("unchecked")
    static boolean register(Plugin plugin, BiFunction<CommandSender, String, List<String>> completions) {
        Class<? extends Event> eventType;
        Method getSender;
        Method getBuffer;
        Method setCompletions;
        Method setHandled;
        try {
            eventType = (Class<? extends Event>) Class.forName(EVENT);
            getSender = eventType.getMethod("getSender");
            getBuffer = eventType.getMethod("getBuffer");
            setCompletions = eventType.getMethod("setCompletions", List.class);
            setHandled = eventType.getMethod("setHandled", boolean.class);
        } catch (ReflectiveOperationException | LinkageError plainSpigot) {
            return false;
        }

        Listener listener = new Listener() {
        };
        // HIGH, so a plugin that legitimately owns the name has already had its say.
        Bukkit.getPluginManager().registerEvent(eventType, listener, EventPriority.HIGH, (ignored, event) -> {
            if (!eventType.isInstance(event)) {
                return;
            }
            try {
                CommandSender sender = (CommandSender) getSender.invoke(event);
                String buffer = (String) getBuffer.invoke(event);
                List<String> ours = completions.apply(sender, buffer);
                if (ours == null) {
                    return;
                }
                setCompletions.invoke(event, ours);
                // Without this Paper carries on and lets the Brigadier node answer as well — which is where the
                // other plugin's suggestions were coming from.
                setHandled.invoke(event, true);
            } catch (ReflectiveOperationException | RuntimeException failed) {
                plugin.getLogger().log(Level.WARNING, "Tab completion bridge failed; suggestions may be wrong", failed);
            }
        }, plugin);
        return true;
    }
}
