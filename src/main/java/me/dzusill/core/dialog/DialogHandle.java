package me.dzusill.core.dialog;

/**
 * A reference to a dialog that has been shown, so the caller can close it early.
 */
public interface DialogHandle {

    /**
     * @return the correlation token, or a synthetic marker when the dialog was served by the fallback path
     */
    String token();

    /**
     * @return {@code false} once the dialog has been submitted, cancelled or expired
     */
    boolean isOpen();

    /**
     * Closes the dialog and resolves its handler as cancelled, if it has not resolved already.
     */
    void close();
}
