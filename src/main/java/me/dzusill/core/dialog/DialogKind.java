package me.dzusill.core.dialog;

import java.util.List;

/**
 * The dialog's shape - which buttons it has and how they are laid out.
 *
 * <p>
 * Each variant maps to one entry in the vanilla {@code minecraft:dialog_type} registry. Sealed with nested records;
 * dispatch with {@code instanceof}, not a pattern-matching switch (Java 17).
 * </p>
 */
public sealed interface DialogKind {

    /**
     * @return the exact {@code type} string vanilla expects
     */
    String wireName();

    /** One informational screen with a single dismiss button. */
    record Notice(DialogButton action) implements DialogKind {

        public Notice {
            DialogValidation.requireNonNull(action, "notice action");
        }

        /** Vanilla defaults the button to a {@code gui.ok} label with no action. */
        public static Notice ok(String label) {
            return new Notice(DialogButton.of(label, new DialogAction.None()));
        }

        @Override
        public String wireName() {
            return "minecraft:notice";
        }
    }

    /**
     * A yes/no prompt. Vanilla requires <strong>both</strong> buttons - it rejects the dialog with <em>"No key no …; No
     * key yes …"</em> otherwise - and treats {@code no} as the escape action.
     */
    record Confirmation(DialogButton yes, DialogButton no) implements DialogKind {

        public Confirmation {
            DialogValidation.requireNonNull(yes, "confirmation yes button");
            DialogValidation.requireNonNull(no, "confirmation no button");
        }

        @Override
        public String wireName() {
            return "minecraft:confirmation";
        }
    }

    /**
     * A grid of buttons. Vanilla requires a non-empty {@code actions} list - it rejects an empty one with <em>"List
     * must have contents"</em>.
     *
     * @param exit
     *            optional footer button, also used as the escape action; {@code null} for none
     */
    record MultiAction(List<DialogButton> actions, int columns, DialogButton exit) implements DialogKind {

        public MultiAction {
            DialogValidation.requireNonNull(actions, "actions");
            if (actions.isEmpty())
                throw new IllegalArgumentException("multi_action requires at least one button");
            DialogValidation.requireColumns(columns);
            actions = List.copyOf(actions);
        }

        public static MultiAction of(List<DialogButton> actions) {
            return new MultiAction(actions, 2, null);
        }

        @Override
        public String wireName() {
            return "minecraft:multi_action";
        }
    }

    /**
     * A list of links to other dialogs, by registry id. Button titles come from each target's {@code external_title}.
     */
    record DialogList(List<String> dialogs, DialogButton exit, int columns, int buttonWidth) implements DialogKind {

        public DialogList {
            DialogValidation.requireNonNull(dialogs, "dialogs");
            if (dialogs.isEmpty())
                throw new IllegalArgumentException("dialog_list requires at least one dialog id");
            DialogValidation.requireColumns(columns);
            DialogValidation.requireWidth(buttonWidth, "button width");
            dialogs = List.copyOf(dialogs);
        }

        @Override
        public String wireName() {
            return "minecraft:dialog_list";
        }
    }

    /** The server-links screen, populated by the server's configured links. */
    record ServerLinks(DialogButton exit, int columns, int buttonWidth) implements DialogKind {

        public ServerLinks {
            DialogValidation.requireColumns(columns);
            DialogValidation.requireWidth(buttonWidth, "button width");
        }

        public static ServerLinks defaults() {
            return new ServerLinks(null, 2, DialogValidation.DEFAULT_BUTTON_WIDTH);
        }

        @Override
        public String wireName() {
            return "minecraft:server_links";
        }
    }
}
