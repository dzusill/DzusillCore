package me.dzusill.core.dialog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Locks the spec's validation to the rules the vanilla codec actually enforces.
 *
 * <p>
 * Each rule here was probed against a live Paper 1.21.11 server via {@code dialog show}; the server's own rejection
 * message is quoted on the test so the intent survives a future API change.
 * </p>
 */
class DialogSpecTest {

    private static DialogSpec.Builder notice() {
        return DialogSpec.builder(DialogKind.Notice.ok("<green>OK"), "Title");
    }

    @Nested
    @DisplayName("rules the vanilla codec enforces")
    class VanillaRules {

        @Test
        @DisplayName("title must be present - vanilla: 'No key title in MapLike[...]'")
        void titleRequired() {
            assertThrows(IllegalArgumentException.class,
                    () -> DialogSpec.builder(DialogKind.Notice.ok("OK"), null).build());
        }

        @Test
        @DisplayName("an empty title is allowed - it hides the header, which is a real design")
        void emptyTitleAllowed() {
            assertDoesNotThrow(() -> DialogSpec.builder(DialogKind.Notice.ok("OK"), "").build());
        }

        @Test
        @DisplayName("multi_action needs a button - vanilla: 'List must have contents'")
        void multiActionNeedsButtons() {
            assertThrows(IllegalArgumentException.class, () -> DialogKind.MultiAction.of(List.of()));
        }

        @Test
        @DisplayName("input key charset - vanilla: 'bad-key! is not a valid input name'")
        void inputKeyCharset() {
            assertThrows(IllegalArgumentException.class, () -> DialogInput.Text.of("bad-key!", "L"));
            assertThrows(IllegalArgumentException.class, () -> DialogInput.Text.of("has space", "L"));
            assertThrows(IllegalArgumentException.class, () -> DialogInput.Text.of("dots.not.allowed", "L"));
            assertDoesNotThrow(() -> DialogInput.Text.of("good_KEY_123", "L"));
        }

        @Test
        @DisplayName("afterAction NONE with pause - vanilla: 'Dialogs that pause the game must use after_action "
                + "values that unpause it after user action!'")
        void noneRequiresUnpaused() {
            assertThrows(IllegalArgumentException.class, () -> new DialogSpec(DialogKind.Notice.ok("OK"), "T", null,
                    List.of(), List.of(), true, AfterAction.NONE, true));
        }

        @Test
        @DisplayName("builder clears pause automatically when NONE is selected")
        void builderClearsPauseForNone() {
            DialogSpec spec = notice().afterAction(AfterAction.NONE).build();
            assertFalse(spec.pause());
            assertEquals(AfterAction.NONE, spec.afterAction());
        }

        @Test
        @DisplayName("single_option needs at least one option, and at most one initial")
        void singleOptionRules() {
            assertThrows(IllegalArgumentException.class, () -> DialogInput.SingleOption.of("k", "L", List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> DialogInput.SingleOption.of("k", "L",
                            List.of(new DialogInput.SingleOption.Option("a", null, true),
                                    new DialogInput.SingleOption.Option("b", null, true))));
        }

        @Test
        @DisplayName("widths are bounded 1..1024")
        void widthBounds() {
            assertThrows(IllegalArgumentException.class,
                    () -> DialogButton.of("L", new DialogAction.None()).withWidth(0));
            assertThrows(IllegalArgumentException.class,
                    () -> DialogButton.of("L", new DialogAction.None()).withWidth(1025));
            assertDoesNotThrow(() -> DialogButton.of("L", new DialogAction.None()).withWidth(1024));
        }

        @Test
        @DisplayName("number_range needs end > start and an in-range initial")
        void numberRangeBounds() {
            assertThrows(IllegalArgumentException.class, () -> DialogInput.NumberRange.of("k", "L", 5f, 5f, 1f, 5f));
            assertThrows(IllegalArgumentException.class, () -> DialogInput.NumberRange.of("k", "L", 1f, 10f, 1f, 99f));
            assertDoesNotThrow(() -> DialogInput.NumberRange.ofInts("k", "L", 1, 64, 1));
        }
    }

    @Nested
    @DisplayName("rules vanilla does NOT enforce but we do")
    class ExtraRules {

        @Test
        @DisplayName("duplicate input keys would silently collide on the wire")
        void duplicateKeysRejected() {
            assertThrows(IllegalArgumentException.class, () -> notice().input(DialogInput.Text.of("name", "A"))
                    .input(DialogInput.Text.of("name", "B")).build());
        }

        @Test
        @DisplayName("the dz_ prefix is reserved for control data the backend adds")
        void reservedPrefixRejected() {
            assertThrows(IllegalArgumentException.class, () -> DialogInput.Text.of("dz_btn", "L"));
            assertThrows(IllegalArgumentException.class, () -> DialogInput.Bool.of("DZ_anything", "L", false));
        }
    }

    @Nested
    class ButtonOrdering {

        @Test
        @DisplayName("confirmation exposes yes then no, in that order")
        void confirmationOrder() {
            DialogSpec spec = DialogSpec.builder(
                    new DialogKind.Confirmation(DialogButton.callback("Yes", "yes"), DialogButton.callback("No", "no")),
                    "T").build();
            assertEquals(List.of("Yes", "No"), spec.buttons().stream().map(DialogButton::label).toList());
        }

        @Test
        @DisplayName("multi_action exposes actions then the exit button")
        void multiActionOrder() {
            DialogKind.MultiAction kind = new DialogKind.MultiAction(
                    List.of(DialogButton.callback("A", "a"), DialogButton.callback("B", "b")), 2,
                    DialogButton.of("Close", new DialogAction.None()));
            DialogSpec spec = DialogSpec.builder(kind, "T").build();
            assertEquals(List.of("A", "B", "Close"), spec.buttons().stream().map(DialogButton::label).toList());
        }

        @Test
        @DisplayName("hasCallback distinguishes a dialog that can call back from one that cannot")
        void hasCallback() {
            assertTrue(DialogSpec.builder(new DialogKind.Confirmation(DialogButton.callback("Y", "yes"),
                    DialogButton.of("N", new DialogAction.None())), "T").build().hasCallback());
            assertFalse(notice().build().hasCallback());
        }
    }

    @Test
    @DisplayName("spec is defensively immutable")
    void immutability() {
        DialogSpec spec = notice().text("hello").input(DialogInput.Text.of("k", "L")).build();
        assertThrows(UnsupportedOperationException.class, () -> spec.body().add(DialogBody.PlainMessage.of("x")));
        assertThrows(UnsupportedOperationException.class, () -> spec.inputs().add(DialogInput.Text.of("z", "L")));
        assertThrows(UnsupportedOperationException.class, () -> spec.buttons().clear());
    }
}
