package me.dzusill.core.dialog;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.module.AbstractModule;
import me.dzusill.core.prompt.ChatPromptService;
import me.dzusill.core.prompt.PromptService;
import me.dzusill.core.scheduler.PlatformTask;
import me.dzusill.core.scheduler.SchedulerService;

/**
 * Wires the dialog subsystem.
 *
 * <p>
 * Place after the foundation module - it resolves {@link SchedulerService} and {@link MessageService} - and before any
 * module whose commands or menus open dialogs.
 * </p>
 *
 * <p>
 * Publishes a {@link DialogService} unconditionally, on every server version. There is no version gate here on purpose:
 * whether a native dialog is possible is decided per call, per player, by the routing service, so call sites never have
 * to branch and a server upgrade needs no code change.
 * </p>
 *
 * <p>
 * Also publishes a {@link PromptService} if nothing else has - the chat fallback needs one, and it is independently
 * useful as the replacement for the per-plugin chat-prompt classes duplicated across the ecosystem.
 * </p>
 */
public final class DialogModule extends AbstractModule {

    /** Expiry is not urgent; a minute of granularity on a two-minute TTL is plenty. */
    private static final long SWEEP_PERIOD_TICKS = 20L * 60L;

    private final DialogFallback explicitFallback;

    private RoutingDialogService dialogs;
    private ChatPromptService ownedPrompts;
    private PlatformTask sweepTask;

    /**
     * Uses the built-in chat fallback.
     */
    public DialogModule(CorePlugin plugin) {
        this(plugin, null);
    }

    /**
     * @param fallback
     *            a custom fallback, e.g. one backed by an existing confirm menu; {@code null} uses the chat fallback
     */
    public DialogModule(CorePlugin plugin, DialogFallback fallback) {
        super(plugin);
        this.explicitFallback = fallback;
    }

    @Override
    public String name() {
        return "Dialogs";
    }

    @Override
    public void onEnable() {
        SchedulerService scheduler = service(SchedulerService.class);
        MessageService messages = service(MessageService.class);

        PromptService prompts = services().find(PromptService.class).orElse(null);
        if (prompts == null) {
            ownedPrompts = new ChatPromptService(plugin, scheduler, messages);
            provide(PromptService.class, ownedPrompts);
            prompts = ownedPrompts;
        }

        // A custom fallback overrides only what it chooses to handle; the chat fallback still catches the rest, so
        // supplying one cannot accidentally disable text prompts.
        DialogFallback chat = new ChatDialogFallback(messages, prompts);
        DialogFallback fallback = explicitFallback == null ? chat : explicitFallback.orElse(chat);

        dialogs = new RoutingDialogService(plugin, scheduler, new PendingDialogs(), fallback);
        dialogs.forceFallback(plugin.getConfig().getBoolean("dialogs.force-fallback", false));
        provide(DialogService.class, dialogs);

        plugin.getServer().getPluginManager().registerEvents(new LifecycleListener(), plugin);
        sweepTask = scheduler.repeating(dialogs::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);

        plugin.getLogger().info("Dialogs ready (prompt=" + prompts.kind()
                + (dialogs.isForcingFallback() ? ", force-fallback=on" : "") + ")");
    }

    @Override
    public void onDisable() {
        if (sweepTask != null)
            sweepTask.cancel();
        if (dialogs != null) {
            // Resolves every in-flight handler as cancelled rather than leaving callers waiting forever.
            dialogs.reload();
        }
        if (ownedPrompts != null)
            ownedPrompts.shutdown();
    }

    /**
     * Self-registered cleanup, mirroring {@code MenuManager}: the subsystem owns its own hygiene rather than trusting
     * every caller to remember it.
     */
    private final class LifecycleListener implements Listener {

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            dialogs.forget(event.getPlayer().getUniqueId());
        }

        /**
         * A backend lives in another plugin. If that plugin unloads, our cached reference points into a dead class
         * loader and the next click would fail with {@code NoClassDefFoundError}; dropping it forces a clean
         * re-resolve.
         */
        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            dialogs.invalidateBackend();
        }
    }
}
