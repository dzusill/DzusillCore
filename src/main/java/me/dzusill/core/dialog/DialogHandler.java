package me.dzusill.core.dialog;

import me.dzusill.core.dialog.spi.DialogValues;

/**
 * Called when a dialog is resolved.
 *
 * <p>
 * <strong>Exactly one</strong> of {@link #onSubmit} / {@link #onCancel()} runs, exactly once - unless the spec uses
 * {@link AfterAction#NONE}, which deliberately leaves the screen open so the player may submit repeatedly. Cancellation
 * covers every non-submit ending: the player closed the screen, the token expired, they disconnected, or the owning
 * plugin reloaded.
 * </p>
 *
 * <p>
 * Handlers run on the player's thread, so they may touch the Bukkit API directly.
 * </p>
 */
@FunctionalInterface
public interface DialogHandler {

    /**
     * @param buttonId
     *            the {@link DialogAction.Callback#buttonId()} of the activated button, so one handler can serve several
     *            buttons
     * @param values
     *            the submitted input values - untrusted, re-validate before use
     */
    void onSubmit(String buttonId, DialogValues values);

    default void onCancel() {
        // Most callers only care about submission.
    }
}
