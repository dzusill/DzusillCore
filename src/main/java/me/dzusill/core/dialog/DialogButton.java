package me.dzusill.core.dialog;

/**
 * A clickable button.
 *
 * @param label
 *            MiniMessage text shown on the button
 * @param tooltip
 *            MiniMessage text shown on hover; {@code null} for none
 * @param width
 *            1-1024, vanilla default 150
 * @param action
 *            what activating the button does
 */
public record DialogButton(String label, String tooltip, int width, DialogAction action) {

    public DialogButton {
        label = DialogValidation.requireText(label, "button label");
        DialogValidation.requireWidth(width, "button width");
        DialogValidation.requireNonNull(action, "button action");
    }

    /**
     * Button with the vanilla default width and no tooltip.
     */
    public static DialogButton of(String label, DialogAction action) {
        return new DialogButton(label, null, DialogValidation.DEFAULT_BUTTON_WIDTH, action);
    }

    public static DialogButton of(String label, String tooltip, DialogAction action) {
        return new DialogButton(label, tooltip, DialogValidation.DEFAULT_BUTTON_WIDTH, action);
    }

    /**
     * Button that reports back to the server under {@code buttonId}.
     */
    public static DialogButton callback(String label, String buttonId) {
        return of(label, new DialogAction.Callback(buttonId));
    }

    public DialogButton withWidth(int newWidth) {
        return new DialogButton(label, tooltip, newWidth, action);
    }

    public DialogButton withTooltip(String newTooltip) {
        return new DialogButton(label, newTooltip, width, action);
    }
}
