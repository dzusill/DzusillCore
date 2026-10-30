package me.dzusill.core.dialog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;

import me.dzusill.core.dialog.spi.DialogBackend;
import me.dzusill.core.dialog.spi.DialogCallbackSink;
import me.dzusill.core.dialog.spi.DialogValues;

/**
 * The one sink every backend is attached to, no matter how many plugins are rendering dialogs.
 *
 * <h2>The bug this exists to stop</h2>
 *
 * <p>
 * A {@link DialogBackend} is registered with Bukkit's {@code ServicesManager} by one plugin and resolved by
 * <em>every</em> plugin: core is a shared library, so each plugin has its own {@link RoutingDialogService} and its own
 * {@link PendingDialogs}, but they all look up the same highest-priority backend <strong>instance</strong>. A backend
 * keeps a single sink field, so when the second plugin resolved it and called {@code attach(this)}, it overwrote the
 * first plugin's sink — and from then on every click, from every plugin's dialog, was reported to whichever registry
 * attached last. The other plugins' tokens were unknown there, so their buttons were logged as "token is unknown,
 * expired or already used" and did nothing: the screen closed, the handler never ran, and a player waiting on a choice
 * screen was stuck with no way to answer.
 * </p>
 *
 * <p>
 * Backends are therefore handed <em>this</em> — a process-wide multiplexer — and every registry registers itself here
 * instead. A response is offered to each registry in turn and the one that minted the token claims it; the rest see a
 * token they do not know and decline, which costs a map lookup. Attaching twice is now harmless, because both plugins
 * attach the same object.
 * </p>
 *
 * <p>
 * The instance is static, and that is exactly what makes it work: core is deployed as one plugin and its classes are
 * loaded once, so every plugin that talks to {@code me.dzusill.core.dialog} shares this class and this field.
 * </p>
 *
 * <h2>References are weak</h2>
 *
 * <p>
 * A static registry holding a disabled plugin's service would pin that plugin's class loader for the lifetime of the
 * server — the classic {@code /reload} leak. Registrations are weak, so forgetting to unregister costs nothing
 * permanent; {@link #unregister(ClaimingSink)} is still called on disable so the entry goes at once rather than at the
 * next GC.
 * </p>
 */
public final class DialogSinks implements DialogCallbackSink {

    /** A forged-click flood is cheap to send and cheap to drop; this bounds the log noise it can cause. */
    private static final int MAX_CLICKS_PER_SECOND = 10;

    private static final DialogSinks SHARED = new DialogSinks();

    private final List<WeakReference<ClaimingSink>> sinks = new CopyOnWriteArrayList<>();
    private final Map<UUID, long[]> clickWindows = new ConcurrentHashMap<>();

    private DialogSinks() {
    }

    /** @return the multiplexer every backend is attached to */
    public static DialogSinks shared() {
        return SHARED;
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    void register(ClaimingSink sink) {
        if (sink == null)
            return;
        for (ClaimingSink known : live()) {
            if (known == sink)
                return; // re-resolving a backend must not double-register
        }
        sinks.add(new WeakReference<>(sink));
    }

    void unregister(ClaimingSink sink) {
        sinks.removeIf(reference -> {
            ClaimingSink held = reference.get();
            return held == null || held == sink;
        });
    }

    /** @return how many registries are currently listening; for tests and diagnostics */
    public int size() {
        return live().size();
    }

    private List<ClaimingSink> live() {
        List<ClaimingSink> alive = new ArrayList<>(sinks.size());
        boolean collected = false;
        for (WeakReference<ClaimingSink> reference : sinks) {
            ClaimingSink sink = reference.get();
            if (sink == null)
                collected = true;
            else
                alive.add(sink);
        }
        if (collected)
            sinks.removeIf(reference -> reference.get() == null);
        return alive;
    }

    // -------------------------------------------------------------------------
    // DialogCallbackSink
    // -------------------------------------------------------------------------

    @Override
    public void onSubmit(UUID playerId, String token, String buttonId, DialogValues values) {
        if (playerId == null || token == null)
            return;
        if (isRateLimited(playerId)) {
            logger().warning("[Dialogs] Dropping dialog responses from " + playerId + ": too many in one second");
            return;
        }
        List<ClaimingSink> targets = live();
        for (ClaimingSink sink : targets) {
            try {
                if (sink.trySubmit(playerId, token, buttonId, values))
                    return;
            } catch (Exception | LinkageError failure) {
                logger().log(Level.SEVERE, "[Dialogs] A dialog registry failed while handling a response", failure);
            }
        }
        // Unknown, expired, already used, or forged on someone else's behalf. Never act on it - but say so, because
        // a button that does nothing otherwise leaves no evidence at all.
        logger().warning("[Dialogs] Ignoring a dialog response from " + playerId + ": none of the " + targets.size()
                + " registered dialog registries knows its token. It is expired, already used, or forged.");
    }

    @Override
    public void onCancelled(UUID playerId, String token) {
        if (token == null)
            return;
        for (ClaimingSink sink : live()) {
            try {
                if (sink.tryCancel(playerId, token))
                    return;
            } catch (Exception | LinkageError failure) {
                logger().log(Level.SEVERE, "[Dialogs] A dialog registry failed while cancelling", failure);
            }
        }
    }

    /** Drops the click budget of a player who has left. */
    void forget(UUID playerId) {
        clickWindows.remove(playerId);
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

    /**
     * The server logger rather than a plugin's: a response that nobody claimed belongs to no plugin by definition, and
     * blaming whichever plugin happened to attach the backend is how this bug stayed hidden in the first place.
     */
    private static Logger logger() {
        return Bukkit.getLogger();
    }
}
