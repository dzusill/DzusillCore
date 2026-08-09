package me.dzusill.core.dialog;

import org.bukkit.entity.Player;

/**
 * Serves a dialog without a dialog - on an old server, an old client, or when no rendering backend is installed.
 *
 * <p>
 * This is what keeps every call site version-agnostic: plugin code calls {@link DialogService} unconditionally and
 * never branches on server version. Implementations map a spec onto whatever the server can actually do, typically a
 * chat prompt or a chest menu.
 * </p>
 *
 * <p>
 * A fallback that takes a dialog <strong>owns its handler</strong> and must resolve it exactly once, exactly as the
 * native path would.
 * </p>
 */
@FunctionalInterface
public interface DialogFallback {

    /**
     * @return {@code true} if this fallback took ownership of the dialog and will resolve the handler; {@code false} if
     *         it cannot represent this spec, in which case the caller keeps its own code path
     */
    boolean handle(Player player, DialogSpec spec, DialogHandler handler);

    /**
     * A fallback that never handles anything - dialogs simply do not appear when unsupported.
     */
    static DialogFallback none() {
        return (player, spec, handler) -> false;
    }
}
