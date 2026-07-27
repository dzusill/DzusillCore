package me.dzusill.core.message;

import java.lang.reflect.Method;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Hands a component to the server's <em>own</em> Adventure, bypassing adventure-platform entirely.
 *
 * <p>
 * Paper has shipped Adventure natively since 1.16: its {@code CommandSender} already implements
 * {@code net.kyori.adventure.audience.Audience}. The only reason this framework ever needed a bridge is that the build
 * relocates {@code net.kyori} to {@code me.dzusill.core.lib.kyori} — our {@link Component} is therefore a different
 * class from the server's, so handing it over directly is impossible and {@code instanceof Audience} never matches.
 * </p>
 *
 * <p>
 * JSON is the version-proof way across that boundary: serialize with our relocated Gson serializer, deserialize with
 * the server's, send. Both sides speak the same wire format, so nothing is lost — click and hover events, hex colors,
 * fonts and translatable components all survive. Unlike adventure-platform, which reflects into CraftBukkit internals
 * and needs a new release for every Minecraft version, this only touches Adventure's own stable public API and keeps
 * working on server versions that did not exist when this was written.
 * </p>
 *
 * <p>
 * Everything is resolved reflectively because the framework compiles against the Spigot 1.16.5 API surface, which has
 * neither the native Adventure classes nor {@code Audience#sendMessage}. On plain Spigot none of it resolves and
 * {@link #available()} reports false, leaving {@link MessageService} to fall back to adventure-platform.
 * </p>
 */
final class NativeAdventure {

    private static final Object NATIVE_GSON;
    private static final Method NATIVE_DESERIALIZE;
    private static final Class<?> NATIVE_AUDIENCE;
    private static final Method NATIVE_SEND;

    static {
        Object gson = null;
        Method deserialize = null;
        Class<?> audience = null;
        Method send = null;
        try {
            Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
            gson = serializer.getMethod("gson").invoke(null);
            deserialize = serializer.getMethod("deserialize", String.class);
            Class<?> component = Class.forName("net.kyori.adventure.text.Component");
            audience = Class.forName("net.kyori.adventure.audience.Audience");
            send = audience.getMethod("sendMessage", component);
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Plain Spigot, or a server without native Adventure. Not an error — MessageService falls back.
            gson = null;
            deserialize = null;
            audience = null;
            send = null;
        }
        NATIVE_GSON = gson;
        NATIVE_DESERIALIZE = deserialize;
        NATIVE_AUDIENCE = audience;
        NATIVE_SEND = send;
    }

    private NativeAdventure() {
    }

    /** @return {@code true} when the server exposes its own Adventure, i.e. on Paper and every fork of it */
    static boolean available() {
        return NATIVE_GSON != null && NATIVE_SEND != null;
    }

    /**
     * Sends {@code component} through the server's native Adventure.
     *
     * @param recipient
     *            the target; must be an instance of the server's own {@code Audience}
     * @param component
     *            our relocated component
     * @return {@code true} if the message was delivered, {@code false} if this path does not apply to this recipient or
     *         the reflective hand-off failed — in which case the caller must fall back rather than drop the message
     */
    static boolean send(CommandSender recipient, Component component) {
        if (!available() || !NATIVE_AUDIENCE.isInstance(recipient)) {
            return false;
        }
        try {
            String json = GsonComponentSerializer.gson().serialize(component);
            Object nativeComponent = NATIVE_DESERIALIZE.invoke(NATIVE_GSON, json);
            NATIVE_SEND.invoke(recipient, nativeComponent);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return false;
        }
    }
}
