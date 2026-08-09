package me.dzusill.core.dialog.spi;

import java.util.UUID;

/**
 * Where a backend reports dialog responses. Implemented by core.
 *
 * <p>
 * The backend does no validation and keeps no state: it decodes the packet, resolves who genuinely sent it, and passes
 * both along. Core owns the token registry, so core is the only place that can decide whether a response is legitimate.
 * </p>
 */
public interface DialogCallbackSink {

    /**
     * A player activated a callback button.
     *
     * @param playerId
     *            the player the packet <strong>genuinely came from</strong>, resolved from the connection - never a
     *            value read out of the payload, which is attacker-controlled
     * @param token
     *            the correlation id the backend was given in {@code show}
     * @param buttonId
     *            which button, as declared in the spec
     * @param values
     *            the submitted input values, untrusted
     */
    void onSubmit(UUID playerId, String token, String buttonId, DialogValues values);

    /**
     * The player dismissed the dialog without submitting, or the backend can no longer track it.
     */
    void onCancelled(UUID playerId, String token);
}
