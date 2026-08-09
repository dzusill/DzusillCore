package me.dzusill.core.dialog;

/**
 * A block of content in the dialog's body.
 *
 * <p>
 * Sealed with nested records; dispatch with {@code instanceof}, not a pattern-matching switch (Java 17).
 * </p>
 */
public sealed interface DialogBody {

    /**
     * A multiline label.
     *
     * <p>
     * Vanilla note: body text does <strong>not</strong> support NBT, score or selector components.
     * </p>
     *
     * @param contents
     *            MiniMessage text. May be empty: an empty line is how a dialog gets vertical space between sections,
     *            and vanilla accepts one. Rejecting it here made a documented blank spacer throw at open time instead
     *            of rendering - the same mistake as forbidding an empty title, which vanilla also allows.
     * @param width
     *            1-1024, vanilla default 200
     */
    record PlainMessage(String contents, int width) implements DialogBody {
        public PlainMessage {
            contents = DialogValidation.requireNonNull(contents, "body contents");
            DialogValidation.requireWidth(width, "body width");
        }

        public static PlainMessage of(String contents) {
            return new PlainMessage(contents, DialogValidation.DEFAULT_BODY_WIDTH);
        }
    }

    /**
     * An item icon with an optional description beside it.
     *
     * <p>
     * Carries a namespaced item id rather than an {@code ItemStack} on purpose: core has no business knowing how to
     * serialise item data components, and the rendering backend has the server API to do it properly. A backend may
     * offer its own {@code fromItemStack} helper.
     * </p>
     *
     * <p>
     * Vanilla note: {@code width}/{@code height} are accepted but the icon does not actually scale.
     * </p>
     *
     * @param itemId
     *            namespaced id, e.g. {@code minecraft:diamond}
     * @param description
     *            MiniMessage text shown beside the icon; {@code null} for none
     */
    record Item(String itemId, int count, String description, boolean showDecoration, boolean showTooltip, int width,
            int height) implements DialogBody {

        public Item {
            itemId = DialogValidation.requireText(itemId, "item id");
            if (count < 1)
                throw new IllegalArgumentException("item count must be positive, got " + count);
            DialogValidation.requireWidth(width, "item width");
            DialogValidation.requireWidth(height, "item height");
        }

        public static Item of(String itemId, int count, String description) {
            return new Item(itemId, count, description, true, true, DialogValidation.DEFAULT_BODY_WIDTH,
                    DialogValidation.DEFAULT_BODY_WIDTH);
        }
    }
}
