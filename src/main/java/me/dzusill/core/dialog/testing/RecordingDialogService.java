package me.dzusill.core.dialog.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import me.dzusill.core.dialog.DialogButton;
import me.dzusill.core.dialog.DialogHandle;
import me.dzusill.core.dialog.DialogHandler;
import me.dzusill.core.dialog.DialogKind;
import me.dzusill.core.dialog.DialogService;
import me.dzusill.core.dialog.DialogSpec;
import me.dzusill.core.dialog.spi.DialogValues;

/**
 * A {@link DialogService} that records instead of rendering, and lets a test drive the response.
 *
 * <p>
 * Shipped in main sources on purpose. Core publishes no test-jar, and adding one would mean a {@code tests} classifier
 * dependency in every consumer pom; the ecosystem's existing answer to shared test helpers was to copy them into six
 * repositories, which is exactly what this avoids. It costs a couple of kilobytes in the shaded jar.
 * </p>
 *
 * <p>
 * Downstream plugins inject this in place of the real service and assert both halves of a flow: that the right spec was
 * built, and that clicking a button produces the right state change - with no server, no client and no dialog API.
 * </p>
 *
 * <pre>
 * RecordingDialogService dialogs = new RecordingDialogService();
 * warps.requestDelete(player, "spawn");
 * assertEquals("Delete warp?", dialogs.last().spec().title());
 * dialogs.submit(0, "yes", Map.of());
 * assertFalse(warps.exists("spawn"));
 * </pre>
 */
public final class RecordingDialogService implements DialogService {

    /** One recorded {@code show} call. */
    public record Shown(Player player, DialogSpec spec, DialogHandler handler) {
    }

    private final List<Shown> shown = new ArrayList<>();

    private boolean available = true;
    private boolean supported = true;

    /**
     * Set {@code false} to simulate a server with no rendering backend, driving callers down the fallback branch.
     */
    public void setAvailable(boolean value) {
        this.available = value;
    }

    /**
     * Set {@code false} to simulate a client too old to render dialogs.
     */
    public void setSupported(boolean value) {
        this.supported = value;
    }

    public List<Shown> shown() {
        return List.copyOf(shown);
    }

    public Shown last() {
        if (shown.isEmpty())
            throw new IllegalStateException("no dialog has been shown");
        return shown.get(shown.size() - 1);
    }

    public int count() {
        return shown.size();
    }

    public void clear() {
        shown.clear();
    }

    /**
     * Simulates the player activating a button.
     *
     * @param values
     *            raw input values; read by runtime type, so pass a {@link Boolean} for a checkbox and a {@link Number}
     *            for a slider
     */
    public void submit(int index, String buttonId, Map<String, Object> values) {
        shown.get(index).handler().onSubmit(buttonId, DialogValues.of(values));
    }

    /** Simulates the player activating the most recent dialog's button. */
    public void submitLast(String buttonId, Map<String, Object> values) {
        submit(shown.size() - 1, buttonId, values);
    }

    /** Simulates the player dismissing the dialog. */
    public void cancel(int index) {
        shown.get(index).handler().onCancel();
    }

    public void cancelLast() {
        cancel(shown.size() - 1);
    }

    // -------------------------------------------------------------------------
    // DialogService
    // -------------------------------------------------------------------------

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public boolean supports(Player player) {
        return available && supported;
    }

    @Override
    public Optional<DialogHandle> show(Player player, DialogSpec spec, DialogHandler handler) {
        if (!available || !supported)
            return Optional.empty();
        shown.add(new Shown(player, spec, handler));
        return Optional.of(new RecordedHandle(shown.size() - 1));
    }

    @Override
    public void close(Player player) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).player().equals(player))
                shown.get(i).handler().onCancel();
        }
    }

    @Override
    public void notice(Player player, String title, String body, String buttonLabel, Runnable onClose) {
        DialogSpec spec = DialogSpec.builder(new DialogKind.Notice(DialogButton.callback(buttonLabel, "ok")), title)
                .text(body).build();
        record(player, spec, onClose::run, onClose);
    }

    @Override
    public void confirm(Player player, String title, String body, String yesLabel, String noLabel,
            Consumer<Boolean> result) {
        DialogSpec spec = DialogSpec.builder(new DialogKind.Confirmation(DialogButton.callback(yesLabel, "yes"),
                DialogButton.callback(noLabel, "no")), title).text(body).build();
        DialogHandler handler = new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                result.accept("yes".equals(buttonId));
            }

            @Override
            public void onCancel() {
                result.accept(false);
            }
        };
        if (show(player, spec, handler).isEmpty())
            result.accept(false);
    }

    @Override
    public void input(Player player, String title, String label, String initial, int maxLength,
            Consumer<String> result) {
        DialogSpec spec = DialogSpec
                .builder(new DialogKind.Confirmation(DialogButton.callback("<green>Confirm", "ok"),
                        DialogButton.callback("<red>Cancel", "cancel")), title)
                .input(me.dzusill.core.dialog.DialogInput.Text.of("value", label, maxLength)).build();
        DialogHandler handler = new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                result.accept("ok".equals(buttonId) ? values.textOr("value", "") : "");
            }

            @Override
            public void onCancel() {
                result.accept("");
            }
        };
        if (show(player, spec, handler).isEmpty())
            result.accept("");
    }

    private void record(Player player, DialogSpec spec, Runnable onSubmit, Runnable onCancel) {
        DialogHandler handler = new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, DialogValues values) {
                onSubmit.run();
            }

            @Override
            public void onCancel() {
                onCancel.run();
            }
        };
        if (show(player, spec, handler).isEmpty())
            onCancel.run();
    }

    private final class RecordedHandle implements DialogHandle {

        private final int index;

        private RecordedHandle(int index) {
            this.index = index;
        }

        @Override
        public String token() {
            return "recorded-" + index;
        }

        @Override
        public boolean isOpen() {
            return index < shown.size();
        }

        @Override
        public void close() {
            cancel(index);
        }
    }
}
