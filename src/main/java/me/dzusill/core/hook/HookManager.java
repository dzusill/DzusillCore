package me.dzusill.core.hook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.scheduler.Platforms;
import me.dzusill.core.service.Service;

/**
 * Registers and resolves {@link PluginHook}s. Each registered hook is given a chance to set itself up if its target
 * plugin is present; callers later resolve a hook only when it is active, keeping optional-dependency handling in one
 * place.
 */
public final class HookManager implements Service {

    private final CorePlugin plugin;
    private final Map<Class<? extends PluginHook>, PluginHook> hooks = new LinkedHashMap<>();

    public HookManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a hook for an optional plugin, but only if that plugin is installed and enabled.
     *
     * <p>
     * The {@code factory} is invoked <em>after</em> the presence check, so a hook class that imports a
     * soft-dependency's API types is never loaded by the JVM when that plugin is absent. This is what makes
     * integrations safe in a template used by both trivial plugins (no soft-deps installed) and full-featured ones.
     * Always register via a constructor reference, e.g. {@code register("Vault", VaultHook::new)}.
     * </p>
     *
     * @param pluginName
     *            the soft-dependency's plugin name (as in {@code plugin.yml})
     * @param factory
     *            creates the hook; only called when the plugin is present
     * @return the active hook, or empty if the plugin is absent or setup did not succeed
     */
    public <T extends PluginHook> Optional<T> register(String pluginName, Supplier<T> factory) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            return Optional.empty();
        }
        T hook = factory.get();
        hook.tryEnable();
        hooks.put(hook.getClass(), hook);
        if (hook.isActive()) {
            plugin.getLogger().info("Hooked into " + hook.pluginName());
            return Optional.of(hook);
        }
        return Optional.empty();
    }

    /**
     * Same as {@link #register(String, Supplier)}, but skips the hook entirely on Folia.
     *
     * <p>
     * Several popular integrations (EssentialsX, WorldGuard, CMI) do not support Folia and either fail to load or call
     * the legacy scheduler from our callbacks. Registering them there turns a working server into a crashing one, so
     * the safe behaviour is to run without the integration and say so loudly once at startup.
     * </p>
     *
     * @param pluginName
     *            the soft-dependency's plugin name (as in {@code plugin.yml})
     * @param factory
     *            creates the hook; only called when the plugin is present and the server is not Folia
     * @return the active hook, or empty if skipped, absent, or setup did not succeed
     */
    public <T extends PluginHook> Optional<T> registerUnlessFolia(String pluginName, Supplier<T> factory) {
        if (Platforms.isFolia()) {
            if (Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                plugin.getLogger().warning(pluginName + " integration disabled: " + pluginName
                        + " does not support Folia. " + "Everything else keeps working.");
            }
            return Optional.empty();
        }
        return register(pluginName, factory);
    }

    /**
     * @return the registered hook if it is active, otherwise empty
     */
    @SuppressWarnings("unchecked")
    public <T extends PluginHook> Optional<T> get(Class<T> type) {
        PluginHook hook = hooks.get(type);
        return hook != null && hook.isActive() ? Optional.of((T) hook) : Optional.empty();
    }

    /**
     * @return {@code true} if a hook of the given type is registered and active
     */
    public boolean isActive(Class<? extends PluginHook> type) {
        PluginHook hook = hooks.get(type);
        return hook != null && hook.isActive();
    }
}
