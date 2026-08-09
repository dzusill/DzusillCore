package me.dzusill.core.dialog.spi;

import org.bukkit.entity.Player;

import me.dzusill.core.dialog.DialogSpec;

/**
 * Renders dialogs. Implemented <strong>outside</strong> DzusillCore, by a plugin that is not shaded.
 *
 * <h2>Why this is an SPI and not core code</h2>
 *
 * <p>
 * DzusillCore relocates {@code net.kyori} into {@code me.dzusill.core.lib.kyori}. Every typed dialog builder in the
 * server API takes a native {@code net.kyori} component, so any call core makes to one is rewritten by the shade
 * relocator and fails at runtime. A provider plugin has no such constraint: it can call the typed API directly, and it
 * can use the <em>server's own</em> MiniMessage, which always emits the component format the running server expects.
 * </p>
 *
 * <p>
 * Everything crossing this boundary is therefore a plain core type - {@link DialogSpec} and friends are records of
 * {@link String}, primitives and {@code java.util} collections. No component, no NBT, no SNBT.
 * </p>
 *
 * <h2>Registration</h2>
 *
 * <p>
 * Register with Bukkit's {@code ServicesManager}, not with core's {@code HookManager}. A provider necessarily declares
 * {@code depend: [DzusillCore]}, so core enables <em>first</em> and any eager presence check made during core's startup
 * would always miss. Core resolves the backend lazily on first use and calls {@link #attach(DialogCallbackSink)} once
 * when it does.
 * </p>
 */
public interface DialogBackend {

    /**
     * @return whether this backend can render at all - server version high enough, API classes linked
     */
    boolean available();

    /**
     * @return whether <em>this player's client</em> can render a dialog. A client behind a translating proxy may be
     *         older than the server and cannot, in which case core falls back rather than sending a screen the player
     *         will never see.
     */
    boolean supports(Player player);

    /**
     * Renders the dialog.
     *
     * @param token
     *            opaque correlation id; the backend must echo it back with every callback and must not interpret it
     */
    void show(Player player, DialogSpec spec, String token);

    /** Closes whatever dialog the player currently has open. */
    void close(Player player);

    /**
     * Hands the backend the sink to report submissions to. Called once, when core first resolves this backend.
     */
    void attach(DialogCallbackSink sink);

    /**
     * @return a short identifier for startup logging, e.g. {@code "paper-1.21.11"}
     */
    String describe();
}
