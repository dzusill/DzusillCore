package me.dzusill.core.dialog;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import me.dzusill.core.service.Service;

/**
 * Shows native dialogs, falling back automatically where they are unavailable.
 *
 * <h2>Two tiers, different guarantees</h2>
 *
 * <p>
 * {@link #show(Player, DialogSpec, DialogHandler)} is <strong>best-effort</strong>: it returns an empty
 * {@link Optional} when the dialog can be neither rendered nor mapped onto a fallback, and the caller must keep its own
 * code path for that case.
 * </p>
 *
 * <p>
 * The convenience wrappers - {@link #notice}, {@link #confirm}, {@link #input} - are <strong>guaranteed</strong>: their
 * callback always runs exactly once, on any server version, with or without a rendering backend installed. Cancelling
 * yields {@code false} / an empty string. Prefer these; they are the straight swap for an existing chat prompt or
 * confirm menu.
 * </p>
 *
 * <h2>Text</h2>
 *
 * <p>
 * Every text parameter is MiniMessage, matching {@code MessageService} and the {@code messages.yml} files across the
 * ecosystem, so strings can move between chat and dialogs unchanged.
 * </p>
 *
 * <h2>Trust</h2>
 *
 * <p>
 * Values in a {@link DialogHandler} callback came from the client. Re-validate length, charset and range before acting
 * on them, and never derive authority from them.
 * </p>
 */
public interface DialogService extends Service {

    /**
     * @return whether any player on this server could see a native dialog right now
     */
    boolean available();

    /**
     * @return whether this specific player's client can render one
     */
    boolean supports(Player player);

    /**
     * Shows a dialog.
     *
     * @return a handle, or empty when the dialog could not be shown or mapped onto a fallback
     */
    Optional<DialogHandle> show(Player player, DialogSpec spec, DialogHandler handler);

    /**
     * Closes whatever dialog the player has open, resolving its handler as cancelled.
     */
    void close(Player player);

    /**
     * An informational screen with a single dismiss button. The callback always runs exactly once.
     */
    void notice(Player player, String title, String body, String buttonLabel, Runnable onClose);

    default void notice(Player player, String title, String body) {
        notice(player, title, body, "<green>OK", () -> {
        });
    }

    /**
     * A yes/no prompt. The callback always runs exactly once; dismissing yields {@code false}.
     */
    void confirm(Player player, String title, String body, String yesLabel, String noLabel, Consumer<Boolean> result);

    default void confirm(Player player, String title, String body, Consumer<Boolean> result) {
        confirm(player, title, body, "<green>Confirm", "<red>Cancel", result);
    }

    /**
     * A single free-text field. The callback always runs exactly once; cancelling yields {@code ""}.
     *
     * @param maxLength
     *            enforced by the client <em>and</em> re-enforced server-side before the callback runs
     */
    void input(Player player, String title, String label, String initial, int maxLength, Consumer<String> result);

    default void input(Player player, String title, String label, Consumer<String> result) {
        input(player, title, label, null, 64, result);
    }

    /**
     * Convenience for the common "pick one of these" screen.
     */
    default void chooser(Player player, String title, String body, List<DialogButton> options, DialogHandler handler) {
        show(player, DialogSpec.builder(DialogKind.MultiAction.of(options), title).text(body).build(), handler);
    }
}
