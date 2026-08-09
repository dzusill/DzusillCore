package me.dzusill.core.dialog;

/**
 * What the client does with the dialog screen after the player activates a button.
 *
 * <p>
 * Mirrors the vanilla {@code after_action} field. {@link #NONE} is only legal on a dialog whose {@code pause} flag is
 * {@code false}; the server rejects the combination outright with <em>"Dialogs that pause the game must use
 * after_action values that unpause it after user action!"</em>, so {@link DialogSpec} enforces the same rule up front.
 * </p>
 */
public enum AfterAction {

    /** Close the dialog. The vanilla default. */
    CLOSE("close"),

    /** Leave the dialog open, allowing repeated submissions. Requires {@code pause = false}. */
    NONE("none"),

    /** Replace the dialog with the vanilla "Waiting for Response" screen. */
    WAIT_FOR_RESPONSE("wait_for_response");

    private final String wireName;

    AfterAction(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the exact string vanilla expects in the {@code after_action} field
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @return {@code true} when this value leaves the dialog on screen, so a single dialog may be submitted more than
     *         once
     */
    public boolean allowsRepeatedSubmission() {
        return this == NONE;
    }
}
