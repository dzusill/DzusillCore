package me.dzusill.core.dialog;

import java.util.List;

/**
 * An interactive control whose value is submitted with the dialog.
 *
 * <p>
 * Every input has a {@link #key()} that identifies its value in the submission. Keys must match {@code [A-Za-z0-9_]+} -
 * vanilla rejects anything else with <em>"… is not a valid input name"</em> - and must not use the reserved {@code dz_}
 * prefix.
 * </p>
 *
 * <p>
 * <strong>Read values by declared type, never by probing.</strong> A {@link Bool} submits a byte and a
 * {@link NumberRange} submits a float, so asking for their text yields nothing. That is why the spec keeps its inputs
 * as a first-class list: the response decoder needs the declared types to read the payload correctly.
 * </p>
 */
public sealed interface DialogInput {

    String key();

    String label();

    /** Free text, optionally multiline. */
    record Text(String key, String label, String initial, int maxLength, boolean labelVisible, int width,
            Integer maxLines, Integer height) implements DialogInput {

        public Text {
            key = DialogValidation.requireInputKey(key);
            label = DialogValidation.requireText(label, "input label");
            DialogValidation.requireWidth(width, "input width");
            if (maxLength < 1)
                throw new IllegalArgumentException("maxLength must be positive, got " + maxLength);
            if (height != null && (height < DialogValidation.MIN_MULTILINE_HEIGHT
                    || height > DialogValidation.MAX_MULTILINE_HEIGHT))
                throw new IllegalArgumentException(
                        "multiline height must be between " + DialogValidation.MIN_MULTILINE_HEIGHT + " and "
                                + DialogValidation.MAX_MULTILINE_HEIGHT + ", got " + height);
            if (maxLines != null && maxLines < 1)
                throw new IllegalArgumentException("maxLines must be positive, got " + maxLines);
        }

        public static Text of(String key, String label) {
            return new Text(key, label, null, DialogValidation.DEFAULT_TEXT_MAX_LENGTH, true,
                    DialogValidation.DEFAULT_INPUT_WIDTH, null, null);
        }

        public static Text of(String key, String label, int maxLength) {
            return new Text(key, label, null, maxLength, true, DialogValidation.DEFAULT_INPUT_WIDTH, null, null);
        }

        /** Multiline variant; {@code height} is in pixels (1-512). */
        public static Text multiline(String key, String label, int maxLength, int height) {
            return new Text(key, label, null, maxLength, true, DialogValidation.DEFAULT_INPUT_WIDTH, null, height);
        }

        public boolean isMultiline() {
            return height != null || maxLines != null;
        }
    }

    /**
     * A checkbox.
     *
     * <p>
     * {@code onTrue}/{@code onFalse} are the strings substituted into {@link DialogAction.TemplateCommand} macros; the
     * raw payload tag is {@code 1b}/{@code 0b} regardless.
     * </p>
     */
    record Bool(String key, String label, boolean initial, String onTrue, String onFalse) implements DialogInput {

        public Bool {
            key = DialogValidation.requireInputKey(key);
            label = DialogValidation.requireText(label, "input label");
            onTrue = onTrue == null ? "true" : onTrue;
            onFalse = onFalse == null ? "false" : onFalse;
        }

        public static Bool of(String key, String label, boolean initial) {
            return new Bool(key, label, initial, null, null);
        }
    }

    /** A cycling picker over a fixed option list. */
    record SingleOption(String key, String label, List<Option> options, boolean labelVisible,
            int width) implements DialogInput {

        public SingleOption {
            key = DialogValidation.requireInputKey(key);
            label = DialogValidation.requireText(label, "input label");
            DialogValidation.requireNonNull(options, "options");
            if (options.isEmpty())
                throw new IllegalArgumentException("single_option requires at least one option");
            if (options.stream().filter(Option::initial).count() > 1)
                throw new IllegalArgumentException("only one option may be marked initial");
            DialogValidation.requireWidth(width, "input width");
            options = List.copyOf(options);
        }

        public static SingleOption of(String key, String label, List<Option> options) {
            return new SingleOption(key, label, options, true, DialogValidation.DEFAULT_INPUT_WIDTH);
        }

        /**
         * @param id
         *            the value submitted when this option is selected
         * @param display
         *            MiniMessage label; {@code null} falls back to the id
         */
        public record Option(String id, String display, boolean initial) {
            public Option {
                id = DialogValidation.requireText(id, "option id");
            }

            public static Option of(String id, String display) {
                return new Option(id, display, false);
            }
        }
    }

    /**
     * A slider. Submits a <strong>float</strong>, even when the range is logically integral - read it with
     * {@code intOr} rather than expecting text.
     */
    record NumberRange(String key, String label, float start, float end, Float step, Float initial, String labelFormat,
            int width) implements DialogInput {

        public NumberRange {
            key = DialogValidation.requireInputKey(key);
            label = DialogValidation.requireText(label, "input label");
            if (!(end > start))
                throw new IllegalArgumentException(
                        "number_range end (" + end + ") must be greater than start (" + start + ")");
            if (step != null && step <= 0f)
                throw new IllegalArgumentException("step must be positive, got " + step);
            if (initial != null && (initial < start || initial > end))
                throw new IllegalArgumentException(
                        "initial (" + initial + ") must lie within [" + start + ", " + end + "]");
            DialogValidation.requireWidth(width, "input width");
        }

        public static NumberRange of(String key, String label, float start, float end, float step, float initial) {
            return new NumberRange(key, label, start, end, step, initial, null, DialogValidation.DEFAULT_INPUT_WIDTH);
        }

        /** Integral slider, the common case for quantities. */
        public static NumberRange ofInts(String key, String label, int start, int end, int initial) {
            return of(key, label, start, end, 1f, initial);
        }
    }
}
