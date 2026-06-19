package me.dzusill.core.nms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import me.dzusill.core.nms.version.NoOpNmsAdapter;
import me.dzusill.core.nms.version.ReflectiveNmsAdapter;

/**
 * Ordered registry that picks the right {@link NmsAdapter} for the running {@link MinecraftVersion}.
 *
 * <p>
 * It mirrors {@code HookManager}'s lazy-loading guarantee: a registered factory is invoked only when its version
 * predicate matches, so a 1.16-specific adapter class is never linked by the JVM on a 1.21 server (and vice-versa).
 * Registrations are checked most-recent-first, so a fork can register an override for a specific version that wins over
 * the built-in default — without editing core.
 * </p>
 *
 * <pre>{@code
 * NmsAdapter adapter = NmsAdapters.defaults().register(v -> v.isAtLeast(1, 21), MyMapped1_21Adapter::new) // fork
 *                                                                                                         // override
 *         .select(VersionDetector.detect());
 * }</pre>
 */
public final class NmsAdapters {

    private record Registration(Predicate<MinecraftVersion> match, Function<MinecraftVersion, NmsAdapter> factory) {
    }

    private final List<Registration> registrations = new ArrayList<>();
    private boolean strict;

    private NmsAdapters() {
    }

    /**
     * @return an empty registry (no built-in adapters)
     */
    public static NmsAdapters empty() {
        return new NmsAdapters();
    }

    /**
     * @return a registry pre-loaded with the framework defaults: the reflective adapter for 1.16+ servers, falling back
     *         to a {@link NoOpNmsAdapter} on anything older/unmatched
     */
    public static NmsAdapters defaults() {
        return empty().register(v -> v.isAtLeast(1, 16), ReflectiveNmsAdapter::new);
    }

    /**
     * Registers an adapter factory for the versions matched by {@code match}. The factory is only called when
     * {@link #select(MinecraftVersion)} finds this is the first matching registration. Most-recently-registered wins,
     * so this can override an earlier (e.g. built-in) registration.
     *
     * @return {@code this}, for chaining
     */
    public NmsAdapters register(Predicate<MinecraftVersion> match, Function<MinecraftVersion, NmsAdapter> factory) {
        registrations.add(0, new Registration(match, factory));
        return this;
    }

    /**
     * In strict mode {@link #select(MinecraftVersion)} throws {@link UnsupportedVersionException} when nothing matches,
     * instead of returning a {@link NoOpNmsAdapter}.
     *
     * @return {@code this}, for chaining
     */
    public NmsAdapters strict() {
        this.strict = true;
        return this;
    }

    /**
     * Selects and instantiates the adapter for the given version.
     *
     * @throws UnsupportedVersionException
     *             if no registration matches and strict mode is enabled
     */
    public NmsAdapter select(MinecraftVersion version) {
        for (Registration registration : registrations) {
            if (registration.match().test(version)) {
                return registration.factory().apply(version);
            }
        }
        if (strict) {
            throw new UnsupportedVersionException(version);
        }
        return new NoOpNmsAdapter(version);
    }
}
