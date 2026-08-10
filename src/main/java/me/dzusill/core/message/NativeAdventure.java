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
    private static final Throwable SETUP_FAILURE;

    /**
     * The server's Adventure package, assembled at runtime rather than written as one literal.
     *
     * <p>
     * This is load-bearing. maven-shade-plugin rewrites string constants that match a relocation pattern, so a plain
     * {@code Class.forName("net.kyori.adventure...")} comes out of the shaded jar naming
     * {@code me.dzusill.core.lib.kyori.adventure...} - our own relocated copy. Every lookup below would then resolve to
     * the classes we are trying to reach *past*, {@code NATIVE_AUDIENCE.isInstance(player)} would always be false, and
     * this whole path would silently do nothing: no click events, no hover events, hex colours downsampled to the
     * nearest of sixteen. Split like this, there is no literal for shade to match.
     * </p>
     */
    private static final String ADVENTURE = String.join(".", "net", "kyori", "adventure");

    static {
        Object gson = null;
        Method deserialize = null;
        Class<?> audience = null;
        Method send = null;
        Throwable failure = null;
        try {
            Class<?> serializer = Class.forName(ADVENTURE + ".text.serializer.gson.GsonComponentSerializer");
            gson = serializer.getMethod("gson").invoke(null);
            deserialize = findDeserialize(serializer);
            Class<?> component = Class.forName(ADVENTURE + ".text.Component");
            audience = Class.forName(ADVENTURE + ".audience.Audience");
            send = audience.getMethod("sendMessage", component);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            // Plain Spigot, or a server without native Adventure. Not an error — MessageService falls back.
            gson = null;
            deserialize = null;
            audience = null;
            send = null;
            failure = ex;
        }
        NATIVE_GSON = gson;
        NATIVE_DESERIALIZE = deserialize;
        NATIVE_AUDIENCE = audience;
        NATIVE_SEND = send;
        SETUP_FAILURE = failure;
    }

    private NativeAdventure() {
    }

    /**
     * Finds {@code deserialize} on the server's Gson serializer.
     *
     * <p>
     * Not {@code getMethod("deserialize", String.class)}: the method is declared on the generic
     * {@code ComponentSerializer<C, C, R>} superinterface, so after erasure its parameter is {@code Object}, not
     * {@code String}. Asking for the {@code String} overload throws {@link NoSuchMethodException} - which used to be
     * swallowed, leaving every message on this framework silently downgraded to a legacy string with no click events,
     * no hover events and hex colours flattened to the nearest of sixteen.
     * </p>
     *
     * <p>
     * Matching by name and arity instead survives both shapes, including a future release that narrows the parameter
     * back to {@code String}.
     * </p>
     */
    private static Method findDeserialize(Class<?> serializer) throws NoSuchMethodException {
        for (Method candidate : serializer.getMethods()) {
            if (candidate.getName().equals("deserialize") && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isAssignableFrom(String.class)) {
                return candidate;
            }
        }
        throw new NoSuchMethodException(serializer.getName() + " has no single-argument deserialize(String)");
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
            lastFailure = ex;
            return false;
        }
    }

    /**
     * Why the last hand-off failed, or {@code null}.
     *
     * <p>
     * Kept because the failure is otherwise invisible: falling back still delivers the message, just without click
     * events, hover events or hex colours. That is a bug report of the form "the buttons do nothing", with nothing in
     * the log to connect it to - so {@link MessageService} reads this and says so once.
     * </p>
     */
    static Throwable lastFailure() {
        return lastFailure;
    }

    private static volatile Throwable lastFailure;

    /** Why the reflective setup failed at class-load time, or {@code null} when it did not. */
    static Throwable setupFailure() {
        return SETUP_FAILURE;
    }
}
