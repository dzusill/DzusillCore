package me.dzusill.core.prompt;

import java.util.function.BiPredicate;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

/**
 * Chat capture via the legacy string-based event.
 *
 * <p>
 * Works on every supported server and needs no reflection - {@code getMessage()} is already a {@link String}, so the
 * relocated-Adventure problem never arises. Deprecated upstream, hence {@link PaperChatCapture} being preferred where
 * available.
 * </p>
 *
 * <p>
 * Listens at {@link EventPriority#LOWEST} deliberately: chat-formatting plugins commonly relay or cancel at
 * {@code HIGHEST}, and a prompt answer must be intercepted before that happens.
 * </p>
 */
@SuppressWarnings("deprecation")
final class LegacyChatCapture implements ChatCapture {

    @Override
    public boolean install(Plugin plugin, BiPredicate<Player, String> onChat) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            public void onChatEvent(AsyncPlayerChatEvent event) {
                if (onChat.test(event.getPlayer(), event.getMessage()))
                    event.setCancelled(true);
            }
        }, plugin);
        return true;
    }

    @Override
    public String kind() {
        return "chat-legacy";
    }
}
