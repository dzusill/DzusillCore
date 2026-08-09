package me.dzusill.core.prompt;

import java.util.function.BiPredicate;

import org.bukkit.plugin.Plugin;

/**
 * Intercepts a player's next chat line.
 *
 * <p>
 * A seam with two implementations because the event to listen on depends on the server, and core cannot simply use the
 * modern one: {@code AsyncChatEvent#message()} returns a native {@code net.kyori} component, while core's own
 * {@code net.kyori} is shaded and relocated, so a direct call would hand us a component of the wrong class. The modern
 * path therefore goes through reflection against string literals the relocator cannot rewrite - the same technique
 * {@code NativeAdventure} uses.
 * </p>
 *
 * <p>
 * Exactly one implementation is installed. Paper fires both events, so installing both would capture every message
 * twice.
 * </p>
 */
interface ChatCapture {

    /**
     * @param onChat
     *            returns {@code true} when the message was consumed and should be hidden from chat
     * @return whether the capture could be installed
     */
    boolean install(Plugin plugin, BiPredicate<org.bukkit.entity.Player, String> onChat);

    String kind();
}
