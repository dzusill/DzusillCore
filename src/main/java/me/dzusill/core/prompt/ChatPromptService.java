package me.dzusill.core.prompt;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.message.Placeholder;
import me.dzusill.core.scheduler.PlatformTask;
import me.dzusill.core.scheduler.SchedulerService;

/**
 * The chat-based {@link PromptService}.
 *
 * <p>
 * Consolidates six near-duplicate per-plugin implementations, keeping the best idea from each and fixing what all of
 * them got wrong:
 * </p>
 *
 * <ul>
 * <li><strong>Exactly-once, funnelled through one method.</strong> Chat, timeout, quit, the cancel keyword and an
 * explicit cancel all resolve through {@link #resolve}, which CAS-guards the callback. Four of the six copies dropped
 * the callback entirely on cancel, so the caller's menu never reopened.</li>
 * <li><strong>A monotonic prompt id.</strong> Only one prior implementation had this. Without it, a timeout belonging
 * to a finished prompt can resolve a newly-issued one.</li>
 * <li><strong>One sweep task, not one task per prompt.</strong> Cheaper, and it cannot leak tasks across a reload.</li>
 * <li><strong>Folia-correct resumption</strong> via {@code atEntity}, so the callback runs on the thread that owns the
 * player.</li>
 * </ul>
 *
 * <p>
 * Commands are deliberately not intercepted, so a captured player can still type {@code /spawn} to get unstuck.
 * </p>
 */
public final class ChatPromptService implements PromptService, Listener {

    private static final long SWEEP_PERIOD_TICKS = 20L;
    private static final long MILLIS_PER_TICK = 50L;

    /** How long the twin of an already-consumed chat line may arrive and still be hidden. */
    private static final long DUPLICATE_WINDOW_MILLIS = 250L;

    private final CorePlugin plugin;
    private final SchedulerService scheduler;
    private final MessageService messages;
    private final LongSupplier clock;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Consumed> recentlyConsumed = new ConcurrentHashMap<>();
    private final AtomicLong nextPromptId = new AtomicLong();

    private final PlatformTask sweepTask;
    private final String kind;

    public ChatPromptService(CorePlugin plugin, SchedulerService scheduler, MessageService messages) {
        this(plugin, scheduler, messages, System::currentTimeMillis);
    }

    /**
     * @param clock
     *            injected so timeout behaviour is testable without waiting
     */
    public ChatPromptService(CorePlugin plugin, SchedulerService scheduler, MessageService messages,
            LongSupplier clock) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.messages = messages;
        this.clock = clock;

        // Self-registered, mirroring MenuManager: the subsystem owns its own cleanup rather than trusting callers.
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Install BOTH rather than picking one. Which chat event a server actually fires is not something we can
        // reliably detect up front - Paper fires the modern one and bridges the legacy one, plain Spigot fires only
        // the legacy one, and some environments (MockBukkit) provide the modern class without ever firing it. Missing
        // the capture would strand a prompted player, so we listen for either and de-duplicate below.
        StringBuilder installed = new StringBuilder();
        if (new PaperChatCapture().install(plugin, this::onChat))
            installed.append("paper");
        if (new LegacyChatCapture().install(plugin, this::onChat))
            installed.append(installed.length() > 0 ? "+legacy" : "legacy");
        this.kind = installed.length() == 0 ? "none" : installed.toString();

        this.sweepTask = scheduler.repeating(this::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    @Override
    public void prompt(Player player, PromptOptions options, Consumer<String> onResult) {
        if (player == null || options == null || onResult == null)
            throw new IllegalArgumentException("player, options and onResult are all required");

        // Replacing a prompt resolves the old one, so its caller is never left waiting.
        cancel(player);

        long id = nextPromptId.incrementAndGet();
        long expiresAt = clock.getAsLong() + options.timeoutTicks() * MILLIS_PER_TICK;
        pending.put(player.getUniqueId(), new Pending(id, options, onResult, expiresAt));

        player.closeInventory();
        messages.sendRaw(player, options.question(), Placeholder.empty());
        messages.sendRaw(player, "<gray><i>Type your answer in chat, or <click:suggest_command:'"
                + options.cancelKeyword() + "'><red>" + options.cancelKeyword() + "</red></click> to cancel.</i>",
                Placeholder.empty());
    }

    @Override
    public boolean isPending(Player player) {
        return player != null && pending.containsKey(player.getUniqueId());
    }

    @Override
    public boolean cancel(Player player) {
        if (player == null)
            return false;
        Pending current = pending.get(player.getUniqueId());
        return current != null && resolve(player.getUniqueId(), current.id, "");
    }

    @Override
    public String kind() {
        return kind;
    }

    /**
     * @return whether the message was consumed and should be hidden from chat
     */
    private boolean onChat(Player player, String message) {
        UUID playerId = player.getUniqueId();
        String trimmed = message == null ? "" : message.trim();

        Pending current = pending.get(playerId);
        if (current == null) {
            // A server that fires both chat events delivers the same line twice. The first delivery consumed the
            // prompt, so this one has nothing to resolve - but it still has to be hidden, or the answer appears in
            // public chat.
            return wasJustConsumed(playerId, trimmed);
        }

        String value = trimmed.equalsIgnoreCase(current.options.cancelKeyword())
                ? ""
                : truncate(trimmed, current.options.maxLength());
        if (!resolve(playerId, current.id, value))
            return wasJustConsumed(playerId, trimmed);

        recentlyConsumed.put(playerId, new Consumed(trimmed, clock.getAsLong()));
        return true;
    }

    private boolean wasJustConsumed(UUID playerId, String message) {
        Consumed last = recentlyConsumed.get(playerId);
        if (last == null)
            return false;
        if (clock.getAsLong() - last.atMillis > DUPLICATE_WINDOW_MILLIS) {
            recentlyConsumed.remove(playerId, last);
            return false;
        }
        return last.message.equals(message);
    }

    /**
     * The single funnel every ending goes through. The id check is what stops a stale timeout resolving a prompt that
     * has already been replaced; the CAS is what makes the callback fire exactly once.
     */
    private boolean resolve(UUID playerId, long id, String value) {
        Pending current = pending.get(playerId);
        if (current == null || current.id != id)
            return false;
        if (!current.finished.compareAndSet(false, true))
            return false;
        pending.remove(playerId, current);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            // Offline: still resolve, just not on an entity thread that no longer exists.
            current.onResult.accept(value);
            return true;
        }
        scheduler.atEntity(player, () -> current.onResult.accept(value), () -> current.onResult.accept(value));
        return true;
    }

    private void sweep() {
        long now = clock.getAsLong();
        for (Map.Entry<UUID, Pending> entry : List.copyOf(pending.entrySet())) {
            if (now >= entry.getValue().expiresAtMillis)
                resolve(entry.getKey(), entry.getValue().id, "");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Pending current = pending.get(playerId);
        if (current != null)
            resolve(playerId, current.id, "");
        recentlyConsumed.remove(playerId);
    }

    /**
     * Cancels everything. Call on disable so no caller is left waiting on a callback that will never come.
     */
    public void shutdown() {
        if (sweepTask != null)
            sweepTask.cancel();
        for (Map.Entry<UUID, Pending> entry : List.copyOf(pending.entrySet()))
            resolve(entry.getKey(), entry.getValue().id, "");
        recentlyConsumed.clear();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /** A chat line already consumed by a prompt, remembered just long enough to hide its duplicate. */
    private static final class Consumed {

        private final String message;
        private final long atMillis;

        private Consumed(String message, long atMillis) {
            this.message = message;
            this.atMillis = atMillis;
        }
    }

    private static final class Pending {

        private final long id;
        private final PromptOptions options;
        private final Consumer<String> onResult;
        private final long expiresAtMillis;
        private final AtomicBoolean finished = new AtomicBoolean();

        private Pending(long id, PromptOptions options, Consumer<String> onResult, long expiresAtMillis) {
            this.id = id;
            this.options = options;
            this.onResult = onResult;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
