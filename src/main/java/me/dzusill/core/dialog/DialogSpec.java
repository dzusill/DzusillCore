package me.dzusill.core.dialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A complete, immutable description of one dialog screen.
 *
 * <p>
 * Deliberately free of any rendering concern: every piece of text is a MiniMessage {@link String}, and there is no
 * reference to Adventure, Paper, or NBT anywhere in the type. That is what lets this class cross from DzusillCore -
 * whose {@code net.kyori} package is shaded and relocated - into an unrelocated rendering backend without the
 * relocation mangling anything.
 * </p>
 *
 * <p>
 * Validation mirrors the vanilla codec, which was probed empirically against Paper 1.21.11 (see
 * {@code DDIALOGS_SURVEY.md} §4.0). The codec silently ignores unknown fields but strictly enforces required ones, so
 * everything it <em>does</em> check is checked here too, at the call site where the stack trace is useful.
 * </p>
 *
 * @param kind
 *            the dialog's shape; also decides which buttons exist
 * @param title
 *            MiniMessage screen title (required by vanilla)
 * @param externalTitle
 *            MiniMessage label used when this dialog is linked from elsewhere; {@code null} falls back to the title
 * @param canCloseWithEscape
 *            vanilla default {@code true}
 * @param pause
 *            whether the dialog pauses a single-player game; vanilla default {@code true}
 */
public record DialogSpec(DialogKind kind, String title, String externalTitle, List<DialogBody> body,
        List<DialogInput> inputs, boolean canCloseWithEscape, AfterAction afterAction, boolean pause) {

    public DialogSpec {
        DialogValidation.requireNonNull(kind, "kind");
        // Vanilla requires the title field to be present, not to be non-empty. An empty title is a legitimate
        // design - the screen's header bar disappears and the content carries itself - so only null is rejected.
        DialogValidation.requireNonNull(title, "title");
        DialogValidation.requireNonNull(afterAction, "afterAction");
        body = body == null ? List.of() : List.copyOf(body);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);

        Set<String> seen = new HashSet<>();
        for (DialogInput input : inputs) {
            if (!seen.add(input.key()))
                throw new IllegalArgumentException("duplicate input key '" + input.key() + "'");
        }

        // Vanilla: "Dialogs that pause the game must use after_action values that unpause it after user action!"
        if (afterAction == AfterAction.NONE && pause)
            throw new IllegalArgumentException(
                    "afterAction NONE requires pause=false, otherwise the client would be stuck on a paused screen");
    }

    /**
     * Every button this dialog renders, in a stable order. The rendering backend uses the index as the button's
     * positional identity on the wire, so this ordering is part of the contract - it must not depend on iteration order
     * of a hash structure.
     */
    public List<DialogButton> buttons() {
        List<DialogButton> out = new ArrayList<>();
        if (kind instanceof DialogKind.Notice notice) {
            out.add(notice.action());
        } else if (kind instanceof DialogKind.Confirmation confirmation) {
            out.add(confirmation.yes());
            out.add(confirmation.no());
        } else if (kind instanceof DialogKind.MultiAction multi) {
            out.addAll(multi.actions());
            if (multi.exit() != null)
                out.add(multi.exit());
        } else if (kind instanceof DialogKind.DialogList list) {
            if (list.exit() != null)
                out.add(list.exit());
        } else if (kind instanceof DialogKind.ServerLinks links) {
            if (links.exit() != null)
                out.add(links.exit());
        }
        return List.copyOf(out);
    }

    /**
     * @return {@code true} when at least one button reports back to the server, i.e. the caller's handler can ever fire
     */
    public boolean hasCallback() {
        return buttons().stream().anyMatch(button -> button.action() instanceof DialogAction.Callback);
    }

    public static Builder builder(DialogKind kind, String title) {
        return new Builder(kind, title);
    }

    /**
     * Mutable builder. Defaults match vanilla: escape allowed, {@code after_action=close}, paused.
     */
    public static final class Builder {

        private final DialogKind kind;
        private final String title;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private String externalTitle;
        private boolean canCloseWithEscape = true;
        private AfterAction afterAction = AfterAction.CLOSE;
        private boolean pause = true;

        private Builder(DialogKind kind, String title) {
            this.kind = kind;
            this.title = title;
        }

        public Builder externalTitle(String value) {
            this.externalTitle = value;
            return this;
        }

        /** Adds a plain-text paragraph at the vanilla default width. */
        public Builder text(String miniMessage) {
            body.add(DialogBody.PlainMessage.of(miniMessage));
            return this;
        }

        public Builder body(DialogBody element) {
            body.add(DialogValidation.requireNonNull(element, "body element"));
            return this;
        }

        public Builder input(DialogInput input) {
            inputs.add(DialogValidation.requireNonNull(input, "input"));
            return this;
        }

        public Builder canCloseWithEscape(boolean value) {
            this.canCloseWithEscape = value;
            return this;
        }

        /**
         * Sets {@code after_action}. Selecting {@link AfterAction#NONE} also clears {@code pause}, because vanilla
         * rejects that combination outright.
         */
        public Builder afterAction(AfterAction value) {
            this.afterAction = DialogValidation.requireNonNull(value, "afterAction");
            if (value == AfterAction.NONE)
                this.pause = false;
            return this;
        }

        public Builder pause(boolean value) {
            this.pause = value;
            return this;
        }

        public DialogSpec build() {
            return new DialogSpec(kind, title, externalTitle, body, inputs, canCloseWithEscape, afterAction, pause);
        }
    }
}
