package me.dzusill.core.prompt;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

/**
 * Chat capture via Paper's modern component-based event.
 *
 * <h2>Why every step is reflective</h2>
 *
 * <p>
 * Two separate problems, both caused by core being shaded:
 * </p>
 *
 * <ol>
 * <li>{@code AsyncChatEvent} does not exist on the API core compiles against, so it cannot be named in a method
 * signature - a listener class mentioning it would fail verification on an older server.</li>
 * <li>{@code AsyncChatEvent#message()} returns a native {@code net.kyori} component. Core's own {@code net.kyori} is
 * relocated to {@code me.dzusill.core.lib.kyori}, so a direct call would be rewritten by the shade relocator and blow
 * up at runtime. The plain-text serializer is therefore resolved through {@link Class#forName} on string literals,
 * which the relocator does not touch - the same trick {@code NativeAdventure} relies on.</li>
 * </ol>
 *
 * <p>
 * If any of that fails to resolve, {@link #install} reports {@code false} and the caller silently uses the legacy
 * capture instead.
 * </p>
 */
final class PaperChatCapture implements ChatCapture {

    private static final String EVENT_CLASS = "io.papermc.paper.event.player.AsyncChatEvent";

    private Class<? extends Event> eventClass;
    private Method messageMethod;
    private Method serializeMethod;
    private Object plainSerializer;

    @Override
    @SuppressWarnings("unchecked")
    public boolean install(Plugin plugin, BiPredicate<Player, String> onChat) {
        try {
            eventClass = (Class<? extends Event>) Class.forName(EVENT_CLASS).asSubclass(Event.class);
            messageMethod = eventClass.getMethod("message");

            // Split so the shade relocator cannot rewrite the constant: it must resolve the SERVER's Adventure.
            Class<?> serializerClass = Class
                    .forName("net." + "kyori.adventure.text.serializer.plain." + "PlainTextComponentSerializer");
            Class<?> componentClass = Class.forName("net." + "kyori.adventure.text.Component");
            plainSerializer = serializerClass.getMethod("plainText").invoke(null);
            serializeMethod = serializerClass.getMethod("serialize", componentClass);
        } catch (Exception | LinkageError unavailable) {
            return false;
        }

        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event))
                return;
            Player player = ((PlayerEvent) event).getPlayer();
            String text = plainText(event);
            if (text != null && onChat.test(player, text))
                ((Cancellable) event).setCancelled(true);
        };

        try {
            plugin.getServer().getPluginManager().registerEvent(eventClass, new Listener() {
            }, EventPriority.LOWEST, executor, plugin, true);
            return true;
        } catch (Exception | LinkageError failure) {
            return false;
        }
    }

    private String plainText(Event event) {
        try {
            return (String) serializeMethod.invoke(plainSerializer, messageMethod.invoke(event));
        } catch (Exception | LinkageError failure) {
            return null;
        }
    }

    @Override
    public String kind() {
        return "chat-paper";
    }
}
