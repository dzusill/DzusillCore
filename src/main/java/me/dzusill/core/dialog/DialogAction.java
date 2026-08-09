package me.dzusill.core.dialog;

/**
 * What happens when a button is activated.
 *
 * <p>
 * Sealed with nested records, so a rendering backend dispatches with an {@code instanceof} chain. <strong>Do not
 * "modernise" that into a pattern-matching switch</strong> - core targets Java 17 and pattern matching for
 * {@code switch} only became final in 21.
 * </p>
 *
 * <p>
 * Only {@link Callback} round-trips back to the server. Everything else is handled entirely by the client, which is why
 * the others carry no handler.
 * </p>
 */
public sealed interface DialogAction {

    /** Opens a URL in the player's browser (the client shows its own confirmation prompt). */
    record OpenUrl(String url) implements DialogAction {
        public OpenUrl {
            url = DialogValidation.requireText(url, "url");
        }
    }

    /**
     * Runs a command <strong>as the player</strong>, with the player's own permissions.
     *
     * <p>
     * Never use this for a privileged action - a player can trigger it by other means. Route anything that needs
     * elevated rights through {@link Callback} and execute it server-side from the handler.
     * </p>
     */
    record RunCommand(String command) implements DialogAction {
        public RunCommand {
            command = DialogValidation.requireText(command, "command");
        }
    }

    /** Pre-fills the player's chat box without sending it. */
    record SuggestCommand(String command) implements DialogAction {
        public SuggestCommand {
            command = DialogValidation.requireText(command, "command");
        }
    }

    /** Copies a literal string to the player's clipboard. */
    record CopyToClipboard(String value) implements DialogAction {
        public CopyToClipboard {
            value = DialogValidation.requireText(value, "value");
        }
    }

    /** Opens another dialog by its registry id. */
    record ShowDialog(String dialogId) implements DialogAction {
        public ShowDialog {
            dialogId = DialogValidation.requireText(dialogId, "dialogId");
        }
    }

    /**
     * Runs a command as the player after substituting {@code $(input_key)} macros with the submitted input values.
     *
     * <p>
     * Carries the same privilege warning as {@link RunCommand}, plus an injection surface: the substituted values come
     * from the client. Prefer {@link Callback}.
     * </p>
     */
    record TemplateCommand(String template) implements DialogAction {
        public TemplateCommand {
            template = DialogValidation.requireText(template, "template");
        }
    }

    /**
     * Sends the submission back to the server, where it is matched to the {@link DialogHandler} registered with the
     * dialog. The {@code buttonId} is echoed to
     * {@link DialogHandler#onSubmit(String, me.dzusill.core.dialog.spi.DialogValues)} so one handler can serve several
     * buttons.
     */
    record Callback(String buttonId) implements DialogAction {
        public Callback {
            buttonId = DialogValidation.requireText(buttonId, "buttonId");
        }
    }

    /** Does nothing beyond honouring the dialog's {@code after_action}. */
    record None() implements DialogAction {
    }
}
