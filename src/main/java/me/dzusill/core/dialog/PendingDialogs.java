package me.dzusill.core.dialog;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Tracks dialogs that have been shown and are waiting for a response.
 *
 * <h2>Why this class is paranoid</h2>
 *
 * <p>
 * A dialog response arrives in a <strong>client-initiated</strong> packet. A modified client can send one at any
 * moment, with any contents, whether or not a dialog is open. If tokens were guessable or not bound to a player, a
 * hacked client could fire <em>another player's</em> pending "confirm disband" or "confirm purchase". So:
 * </p>
 *
 * <ul>
 * <li>tokens are 128 bits of {@link SecureRandom}, never sequential and never derived from a player id;</li>
 * <li>every token is bound to the player it was issued to, and {@link #claim(String, UUID)} rejects a mismatch;</li>
 * <li>a mismatch does <em>not</em> consume the token - otherwise an attacker could burn a victim's pending dialog;</li>
 * <li>tokens are single-use and time-limited.</li>
 * </ul>
 *
 * <h2>Exactly-once</h2>
 *
 * <p>
 * Each entry carries a CAS guard, so exactly one of "claimed" or "cancelled" ever wins - no matter how submission,
 * expiry, disconnect and reload race. This class is pure bookkeeping and never invokes a handler itself; the
 * cancellation methods hand back the entries that transitioned so the caller can fire them on the right thread.
 * </p>
 *
 * <p>
 * The one exception is {@link AfterAction#NONE}, which deliberately leaves the screen open for repeated submission;
 * those entries stay claimable until they expire or are cancelled.
 * </p>
 */
public final class PendingDialogs {

    /** Long enough for a player to read and decide, short enough to bound the attack window. */
    public static final long DEFAULT_TTL_MILLIS = 120_000L;

    private static final int TOKEN_BITS = 128;

    private final Map<String, Pending> byToken = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long ttlMillis;
    private final LongSupplier clock;

    public PendingDialogs() {
        this(DEFAULT_TTL_MILLIS, System::currentTimeMillis);
    }

    /**
     * @param clock
     *            injected so expiry is testable without sleeping
     */
    public PendingDialogs(long ttlMillis, LongSupplier clock) {
        if (ttlMillis <= 0)
            throw new IllegalArgumentException("ttlMillis must be positive, got " + ttlMillis);
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /**
     * Registers a dialog and mints its token.
     *
     * <p>
     * The token is safe to embed in a namespaced-id path: base36 yields only {@code [0-9a-z]}.
     * </p>
     */
    public String issue(UUID playerId, DialogSpec spec, DialogHandler handler) {
        DialogValidation.requireNonNull(playerId, "playerId");
        DialogValidation.requireNonNull(spec, "spec");
        DialogValidation.requireNonNull(handler, "handler");

        String token = newToken();
        byToken.put(token, new Pending(playerId, spec, handler, clock.getAsLong() + ttlMillis));
        return token;
    }

    /**
     * Attempts to consume a token on behalf of the player who actually sent the packet.
     *
     * @param sender
     *            the player the packet genuinely came from - <strong>not</strong> a value read out of the payload
     * @return the entry when the token is known, unexpired, owned by {@code sender}, and not already resolved
     */
    public Optional<Pending> claim(String token, UUID sender) {
        if (token == null || sender == null)
            return Optional.empty();

        Pending pending = byToken.get(token);
        if (pending == null)
            return Optional.empty();

        // Wrong owner: reject WITHOUT consuming, so a forged packet cannot burn the real player's dialog.
        if (!pending.playerId.equals(sender))
            return Optional.empty();

        if (isExpired(pending)) {
            cancel(token);
            return Optional.empty();
        }

        // NONE keeps the screen up on purpose, so the token stays live for further submissions.
        if (pending.spec.afterAction().allowsRepeatedSubmission())
            return Optional.of(pending);

        if (!pending.finished.compareAndSet(false, true))
            return Optional.empty();

        byToken.remove(token);
        return Optional.of(pending);
    }

    /**
     * Resolves a single token as cancelled.
     *
     * @return the entry if this call was the one that resolved it
     */
    public Optional<Pending> cancel(String token) {
        if (token == null)
            return Optional.empty();
        Pending pending = byToken.remove(token);
        if (pending == null || !pending.finished.compareAndSet(false, true))
            return Optional.empty();
        return Optional.of(pending);
    }

    /**
     * Cancels everything belonging to one player. Call on quit.
     */
    public List<Pending> cancelAllFor(UUID playerId) {
        List<Pending> cancelled = new ArrayList<>();
        for (Map.Entry<String, Pending> entry : byToken.entrySet()) {
            if (entry.getValue().playerId.equals(playerId))
                cancel(entry.getKey()).ifPresent(cancelled::add);
        }
        return cancelled;
    }

    /**
     * Cancels every entry past its TTL. Call from a repeating task.
     */
    public List<Pending> sweepExpired() {
        List<Pending> cancelled = new ArrayList<>();
        for (Map.Entry<String, Pending> entry : byToken.entrySet()) {
            if (isExpired(entry.getValue()))
                cancel(entry.getKey()).ifPresent(cancelled::add);
        }
        return cancelled;
    }

    /**
     * Cancels everything. Call on reload and disable, so no handler is left dangling.
     */
    public List<Pending> cancelAll() {
        List<Pending> cancelled = new ArrayList<>();
        for (String token : List.copyOf(byToken.keySet()))
            cancel(token).ifPresent(cancelled::add);
        return cancelled;
    }

    public int size() {
        return byToken.size();
    }

    public boolean isKnown(String token) {
        return token != null && byToken.containsKey(token);
    }

    private boolean isExpired(Pending pending) {
        return clock.getAsLong() >= pending.expiresAtMillis;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BITS / Byte.SIZE];
        random.nextBytes(bytes);
        return new BigInteger(1, bytes).toString(Character.MAX_RADIX);
    }

    /**
     * One dialog awaiting a response. Not a record: the CAS guard is mutable state.
     */
    public static final class Pending {

        private final UUID playerId;
        private final DialogSpec spec;
        private final DialogHandler handler;
        private final long expiresAtMillis;
        private final AtomicBoolean finished = new AtomicBoolean();

        Pending(UUID playerId, DialogSpec spec, DialogHandler handler, long expiresAtMillis) {
            this.playerId = playerId;
            this.spec = spec;
            this.handler = handler;
            this.expiresAtMillis = expiresAtMillis;
        }

        public UUID playerId() {
            return playerId;
        }

        public DialogSpec spec() {
            return spec;
        }

        public DialogHandler handler() {
            return handler;
        }

        public long expiresAtMillis() {
            return expiresAtMillis;
        }
    }
}
