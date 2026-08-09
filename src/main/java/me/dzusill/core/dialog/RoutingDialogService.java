package me.dzusill.core.dialog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.dzusill.core.dialog.spi.DialogBackend;
import me.dzusill.core.dialog.spi.DialogCallbackSink;
import me.dzusill.core.dialog.spi.DialogValues;
import me.dzusill.core.scheduler.SchedulerService;
import me.dzusill.core.service.Reloadable;

/**
 * The default {@link DialogService}: routes each dialog to a rendering backend when one can serve it, and to the
 * fallback when it cannot.
 *
 * <p>
 * Registered once at startup and never re-registered - {@code ServiceRegistry} rejects duplicates and offers no
 * unregister - so reconfiguration mutates this instance through {@link #reload()} instead.
 * </p>
 */
public final class RoutingDialogService implements DialogService, DialogCallbackSink, Reloadable {

    /** A forged-click flood is cheap to send and cheap to drop; this bounds the log noise it can cause. */
    private static final int MAX_CLICKS_PER_SECOND = 10;

    private static final String FALLBACK_TOKEN = "fallback";

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final PendingDialogs pending;
    private final Map<UUID, long[]> clickWindows = new ConcurrentHashMap<>();

    private volatile DialogFallback fallback;
    private volatile boolean forceFallback;

    /**
     * Resolved lazily rather than at startup: a backend plugin necessarily depends on core, so it enables after us and
     * would not yet exist during our own {@code onEnable}.
     */
    private volatile DialogBackend backend;
    private volatile boolean backendResolved;

    public RoutingDialogService(Plugin plugin, SchedulerService scheduler, PendingDialogs pending,
            DialogFallback fallback) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.pending = pending;
        this.fallback = fallback == null ? DialogFallback.none() : fallback;
    }

    // -------------------------------------------------------------------------
    // Backend resolution
    // -------------------------------------------------------------------------

    private Optional<DialogBackend> backend() {
        if (forceFallback)
            return Optional.empty();
        if (!backendResolved) {
            synchronized (this) {
                if (!backendResolved) {
                    backend = resolveBackend();
                    backendResolved = true;
                }
            }
        }
        return Optional.ofNullable(backend);
    }

    private DialogBackend resolveBackend() {
        try {
            RegisteredServiceProvider<DialogBackend> registration = Bukkit.getServicesManager()
                    .getRegistration(DialogBackend.class);
            if (registration == null)
                return null;
            DialogBackend found = registration.getProvider();
            found.attach(this);
            plugin.getLogger().info("Dialog backend: " + found.describe());
            return found;
        } catch (Exception | LinkageError failure) {
            plugin.getLogger().log(Level.WARNING, "Could not resolve a dialog backend; using fallback", failure);
            return null;
        }
    }

    /**
     * Drops the cached backend. Call when the provider plugin disables, otherwise core keeps a reference into a dead
     * class loader and the next click fails with {@code NoClassDefFoundError}.
     */
    public void invalidateBackend() {
        synchronized (this) {
            backend = null;
            backendResolved = false;
        }
    }

    public void fallback(DialogFallback value) {
        this.fallback = value == null ? DialogFallback.none() : value;
    }

    /**
     * Forces every dialog down the fallback path. Exists so both paths can be exercised on one server during QA.
     */
    public void forceFallback(boolean value) {
        this.forceFallback = value;
    }

    public boolean isForcingFallback() {
        return forceFallback;
    }

    // -------------------------------------------------------------------------
    // DialogService
    // -------------------------------------------------------------------------

    @Override
    public boolean available() {
        return backend().filter(DialogBackend::available).isPresent();
    }

    @Override
    public boolean supports(Player player) {
        return backend().filter(DialogBackend::available).filter(candidate -> candidate.supports(player)).isPresent();
    }

    @Override
    public Optional<DialogHandle> show(Player player, DialogSpec spec, DialogHandler handler) {
        DialogValidation.requireNonNull(player, "player");
        DialogValidation.requireNonNull(spec, "spec");
        DialogValidation.requireNonNull(handler, "handler");

        Optional<DialogBackend> target = backend().filter(DialogBackend::available)
                .filter(candidate -> candidate.supports(player));
        if (target.isEmpty())
            return toFallback(player, spec, handler);

        String token = pending.issue(player.getUniqueId(), spec, handler);
        try {
            target.get().show(player, spec, token);
        } catch (Exception | LinkageError failure) {
            // Consume the token WITHOUT firing onCancel: the fallback is about to take ownership of this handler,
            // and resolving it here as well would break the exactly-once contract.
            pending.cancel(token);
            plugin.getLogger().log(Level.SEVERE,
                    "Dialog backend failed to render for " + player.getName() + "; falling back", failure);
            invalidateBackend();
            return toFallback(player, spec, handler);
        }
        return Optional.of(new TokenHandle(token));
    }

    private Optional<DialogHandle> toFallback(Player player, DialogSpec spec, DialogHandler handler) {
        try {
            return fallback.handle(player, spec, handler) ? Optional.of(new FallbackHandle()) : Optional.empty();
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, "Dialog fallback failed for " + player.getName(), failure);
            return Optional.empty();
        }
    }

    @Override
    public void close(Player player) {
        for (PendingDialogs.Pending cancelled : pending.cancelAllFor(player.getUniqueId()))
            dispatch(player, cancelled.handler()::onCancel);
        backend().ifPresent(target -> {
            try {
                target.close(player);
            } catch (Exception | LinkageError failure) {
                plugin.getLogger().log(Level.WARNING, "Dialog backend failed to close for " + player.getName(),
                        failure);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Guaranteed wrappers - the callback always runs exactly once
    // -------------------------------------------------------------------------

    @Override
    public void notice(Player player, String title, String body, String buttonLabel, Runnable onClose) {
        AtomicBoolean done = new AtomicBoolean();
        Runnable once = () -> {
            if (done.compareAndSet(false, true))
                onClose.run();
        };
        DialogSpec spec = DialogSpec.builder(new DialogKind.Notice(DialogButton.callback(buttonLabel, "ok")), title)
                .text(body).build();
        if (show(player, spec, new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                once.run();
            }

            @Override
            public void onCancel() {
                once.run();
            }
        }).isEmpty())
            once.run();
    }

    @Override
    public void confirm(Player player, String title, String body, String yesLabel, String noLabel,
            Consumer<Boolean> result) {
        AtomicBoolean done = new AtomicBoolean();
        Consumer<Boolean> once = value -> {
            if (done.compareAndSet(false, true))
                result.accept(value);
        };
        DialogSpec spec = DialogSpec.builder(new DialogKind.Confirmation(DialogButton.callback(yesLabel, "yes"),
                DialogButton.callback(noLabel, "no")), title).text(body).build();
        if (show(player, spec, new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                once.accept("yes".equals(buttonId));
            }

            @Override
            public void onCancel() {
                once.accept(false);
            }
        }).isEmpty())
            once.accept(false);
    }

    @Override
    public void input(Player player, String title, String label, String initial, int maxLength,
            Consumer<String> result) {
        AtomicBoolean done = new AtomicBoolean();
        Consumer<String> once = value -> {
            if (done.compareAndSet(false, true))
                result.accept(value);
        };
        DialogSpec spec = DialogSpec
                .builder(new DialogKind.Confirmation(DialogButton.callback("<green>Confirm", "ok"),
                        DialogButton.callback("<red>Cancel", "cancel")), title)
                .input(new DialogInput.Text("value", label, initial, maxLength, true,
                        DialogValidation.DEFAULT_INPUT_WIDTH, null, null))
                .build();
        if (show(player, spec, new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                if (!"ok".equals(buttonId)) {
                    once.accept("");
                    return;
                }
                // The client enforces maxLength too, but the payload is attacker-controlled, so enforce it again.
                String text = values.textOr("value", "");
                once.accept(text.length() > maxLength ? text.substring(0, maxLength) : text);
            }

            @Override
            public void onCancel() {
                once.accept("");
            }
        }).isEmpty())
            once.accept("");
    }

    // -------------------------------------------------------------------------
    // DialogCallbackSink
    // -------------------------------------------------------------------------

    @Override
    public void onSubmit(UUID playerId, String token, String buttonId, DialogValues values) {
        if (playerId == null || token == null)
            return;
        if (isRateLimited(playerId)) {
            plugin.getLogger().warning("Dropping dialog responses from " + playerId + ": too many in one second");
            return;
        }

        Optional<PendingDialogs.Pending> claimed = pending.claim(token, playerId);
        if (claimed.isEmpty()) {
            // Unknown, expired, already used, or forged on someone else's behalf. Never act on it - but say so.
            // This used to be logged at FINE, which meant a button that did nothing produced no evidence at all.
            plugin.getLogger().warning("Ignoring a dialog response from " + playerId
                    + ": its token is unknown, expired or already used. If a button appears to do nothing, this is"
                    + " why - the dialog it belongs to is no longer pending.");
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null)
            return;
        DialogValues safe = values == null ? DialogValues.empty() : values;
        dispatch(player, () -> claimed.get().handler().onSubmit(buttonId == null ? "" : buttonId, safe));
    }

    @Override
    public void onCancelled(UUID playerId, String token) {
        pending.cancel(token).ifPresent(cancelled -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null)
                dispatch(player, cancelled.handler()::onCancel);
            else
                cancelled.handler().onCancel();
        });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Resolves everything this player has open as cancelled. Call on quit.
     */
    public void forget(UUID playerId) {
        for (PendingDialogs.Pending cancelled : pending.cancelAllFor(playerId))
            cancelled.handler().onCancel();
        clickWindows.remove(playerId);
    }

    /**
     * Cancels every expired dialog. Call from a repeating task.
     */
    public int sweep() {
        List<PendingDialogs.Pending> expired = pending.sweepExpired();
        for (PendingDialogs.Pending cancelled : expired)
            cancelled.handler().onCancel();
        return expired.size();
    }

    @Override
    public void reload() {
        // Every in-flight handler resolves as cancelled rather than being silently dropped.
        for (PendingDialogs.Pending cancelled : pending.cancelAll())
            cancelled.handler().onCancel();
        clickWindows.clear();
        invalidateBackend();
    }

    private void dispatch(Player player, Runnable task) {
        try {
            scheduler.atEntity(player, task);
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, "Dialog handler failed for " + player.getName(), failure);
        }
    }

    private boolean isRateLimited(UUID playerId) {
        long now = System.currentTimeMillis();
        long[] window = clickWindows.computeIfAbsent(playerId, key -> new long[]{now, 0L});
        synchronized (window) {
            if (now - window[0] >= 1_000L) {
                window[0] = now;
                window[1] = 0L;
            }
            window[1]++;
            return window[1] > MAX_CLICKS_PER_SECOND;
        }
    }

    private final class TokenHandle implements DialogHandle {

        private final String token;

        private TokenHandle(String token) {
            this.token = token;
        }

        @Override
        public String token() {
            return token;
        }

        @Override
        public boolean isOpen() {
            return pending.isKnown(token);
        }

        @Override
        public void close() {
            pending.cancel(token).ifPresent(cancelled -> cancelled.handler().onCancel());
        }
    }

    private static final class FallbackHandle implements DialogHandle {

        @Override
        public String token() {
            return FALLBACK_TOKEN;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {
            // The fallback owns its own lifecycle (chat timeout, menu close); nothing to cancel here.
        }
    }
}
