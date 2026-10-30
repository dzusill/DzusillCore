package me.dzusill.core.dialog;

import java.util.UUID;

import me.dzusill.core.dialog.spi.DialogValues;

/**
 * A dialog registry that can say whether a response was <em>its own</em>.
 *
 * <p>
 * {@link me.dzusill.core.dialog.spi.DialogCallbackSink DialogCallbackSink} returns nothing, which is fine for a backend
 * reporting a click but useless for {@link DialogSinks}: with several plugins sharing one backend, the multiplexer has
 * to hand a response round until somebody recognises the token. That is what these methods answer.
 * </p>
 *
 * <p>
 * Package-private on purpose — it exists for {@link DialogSinks} and {@link RoutingDialogService} to talk to each
 * other, not as something a backend or a plugin implements.
 * </p>
 */
interface ClaimingSink {

    /** @return whether this registry owned {@code token} and dispatched the submission */
    boolean trySubmit(UUID playerId, String token, String buttonId, DialogValues values);

    /** @return whether this registry owned {@code token} and dispatched the cancellation */
    boolean tryCancel(UUID playerId, String token);
}
